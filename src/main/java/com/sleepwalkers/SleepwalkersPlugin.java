package com.sleepwalkers;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Instant Sleepwalkers",
	description = "Hides each sleepwalker at Phosani's Nightmare the moment you kill it",
	tags = {"phosani", "nightmare", "pnm", "sleepwalker", "hide", "instant", "fake", "xp", "drop"}
)
public class SleepwalkersPlugin extends Plugin
{
	// A game tick is 30 client cycles, which is what a projectile's flight is measured in
	private static final int CYCLES_PER_TICK = 30;
	// Nothing in the game reaches further than this, so no distance past it can lengthen a hit
	private static final int MAX_ATTACK_RANGE = 10;

	/**
	 * What a kill is worth. A sleepwalker has 10 hitpoints and dies to any damage, and damage pays
	 * 1 1/3 hitpoints experience a point, so every kill is the same 13 however it was made.
	 */
	private static final int HITPOINTS_XP = 13;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Hooks hooks;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SleepwalkersOverlay overlay;

	@Inject
	private SleepwalkersConfig config;

	// Sleepwalkers currently in the scene. There are only ever a handful, so this is walked rather than
	// keyed on the npc index, which keeps the draw listener off boxing one for every renderable it is
	// handed and is the shorter way round at this size anyway.
	private final List<Sleepwalker> sleepwalkers = new ArrayList<>();
	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	// The tick you last swung on, whoever the swing turned out to be at
	private int swingTick = -1;

	// What you were animating at the end of the last tick, which is how a swing sent again is spotted
	private int lastAnim = -1;
	private int lastAnimFrame;

	// Read on every draw call and every kill, so cached instead of going through the config proxy
	private boolean hideKilled;
	private boolean fakeXpDrops;

	@Provides
	SleepwalkersConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SleepwalkersConfig.class);
	}

	@Override
	protected void startUp()
	{
		loadConfig();
		hooks.registerRenderableDrawListener(drawListener);
		overlayManager.add(overlay);
		clientThread.invoke(this::rescan);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		hooks.unregisterRenderableDrawListener(drawListener);
		clientThread.invoke(this::reset);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (SleepwalkersConfig.GROUP.equals(event.getGroup()))
		{
			loadConfig();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOADING || state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			reset();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		track(event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		for (int i = 0; i < sleepwalkers.size(); i++)
		{
			if (sleepwalkers.get(i).getNpc() == npc)
			{
				sleepwalkers.remove(i);
				return;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		catchSwing();

		for (int i = 0; i < sleepwalkers.size(); i++)
		{
			Sleepwalker sleepwalker = sleepwalkers.get(i);
			if (!sleepwalker.isKilled())
			{
				continue;
			}

			sleepwalker.setHiddenTicks(sleepwalker.getHiddenTicks() + 1);
			// A health ratio of 0 is dead, which is the call coming good. Anything else, including the
			// -1 of a sleepwalker that was never actually hit, is still standing there
			if (sleepwalker.getNpc().getHealthRatio() == 0)
			{
				continue;
			}

			// Still standing past the tick the hit was due, so it was called wrong
			if (sleepwalker.getHiddenTicks() > sleepwalker.getDeadlineTicks() || isEntering(sleepwalker))
			{
				show(sleepwalker);
			}
		}
	}

	/**
	 * A sleepwalker has 10 hitpoints, no defence and -100 in every defence bonus, so a hit on one always
	 * lands and always kills. Swinging at one is the kill, which is why there is no waiting on an XP
	 * drop to tell and nothing to weigh up before hiding it.
	 * <p>
	 * The animation is the earliest the attack shows up anywhere, a tick or more before the projectile
	 * is in the air and the hitsplat lands, so this is what makes the sleepwalker go instantly. What was
	 * swung is never read off, only how far away it was, which {@link #maxHitDelay(int)} turns into the
	 * tick the sleepwalker has to be dead by for the call to have been right.
	 */
	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (sleepwalkers.isEmpty())
		{
			return;
		}

		Actor actor = event.getActor();
		if (actor instanceof NPC)
		{
			Sleepwalker entering = getSleepwalker((NPC) actor);
			if (entering != null && entering.isKilled() && isEntering(entering))
			{
				// She has it, so it comes back the moment the absorb starts rather than on the next tick
				show(entering);
			}

			return;
		}

		Player player = client.getLocalPlayer();
		if (actor != player || player.getAnimation() == -1)
		{
			return;
		}

		// Noted whatever it turns out to have been aimed at, so that a swing the server sent before it
		// sent the target is still a swing when the target lands later in the tick
		swingTick = client.getTickCount();

		// An animation on the sleepwalker you are attacking is a swing at it. Walking there is a pose
		// animation and leaves this one unset, so being out of range doesn't count as a hit.
		markSwing(player, player.getInteracting());
	}

	/**
	 * Every swing, read off the end of the tick it was sent on rather than off an event.
	 * <p>
	 * The game only sends an animation when it is a different one to the one already playing, so the
	 * second and every later attack in a row with the same weapon never arrives as an animation change.
	 * Left at that the kill would fall through to the projectile or the hitsplat, a tick or more behind,
	 * which is the delay. The frame is what gives such a swing away: an attack sent again restarts its
	 * own animation, so the frame drops back to the start where it would otherwise have run on.
	 * <p>
	 * This also covers the other order the server can send things in, a swing arriving before the target
	 * it was aimed at, since by here both have been read. Either way it is still the tick of the swing,
	 * so nothing waits on anything.
	 */
	private void catchSwing()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		int anim = player.getAnimation();
		int frame = player.getAnimationFrame();
		boolean swung = anim != -1 && (anim != lastAnim || frame < lastAnimFrame);
		lastAnim = anim;
		lastAnimFrame = frame;

		if ((swung || swingTick == client.getTickCount()) && !sleepwalkers.isEmpty())
		{
			markSwing(player, player.getInteracting());
		}
	}

	private void markSwing(Player player, Actor target)
	{
		Sleepwalker sleepwalker = target instanceof NPC ? getSleepwalker((NPC) target) : null;
		if (sleepwalker == null || sleepwalker.isKilled())
		{
			return;
		}

		WorldArea from = player.getWorldArea();
		WorldArea to = sleepwalker.getNpc().getWorldArea();
		if (from == null || to == null)
		{
			return;
		}

		markKilled(sleepwalker, maxHitDelay(from.distanceTo(to)));
	}

	/**
	 * The longest any attack can take to land over a distance, which is a spell at
	 * 1 + (1 + distance) / 3 ticks: 1 on the tile beside you, 2 out to 4 tiles, 3 to 7 and 4 to 10.
	 * Bows and crossbows take 1 + (3 + distance) / 6 and thrown weapons and chinchompas
	 * 1 + distance / 6, both of which come in under that at every distance, and melee lands at once.
	 * Taking the slowest of them is what keeps a weapon table out of this. It only ever sets how long a
	 * hidden sleepwalker is given to die before it is called wrong, so erring long is free.
	 */
	private int maxHitDelay(int distance)
	{
		return 1 + (1 + Math.min(distance, MAX_ATTACK_RANGE)) / 3;
	}

	/**
	 * A closer read of the same kill than the animation could give, and the one way a thrall's shot is
	 * spotted at all, since that is a kill of yours you never animate for.
	 * <p>
	 * A projectile's remaining cycles are the flight the server actually sent, so a bow, a crossbow, a
	 * blowpipe, a staff or a thrall each come out at their own delay over their own distance with no
	 * weapon table to keep up to date. The same numbers the wiki gives, for reference: bows and
	 * crossbows take 1 + (3 + distance) / 6 ticks, so 2 out to 8 tiles and 3 past that, thrown weapons
	 * and chinchompas 1 + distance / 6, so 1 out to 5 tiles and 2 past that, and spells
	 * 1 + (1 + distance) / 3, so 2 out to 4 tiles, 3 to 7 and 4 to 10.
	 */
	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		if (sleepwalkers.isEmpty())
		{
			return;
		}

		Projectile projectile = event.getProjectile();
		Actor target = projectile.getTargetActor();
		Sleepwalker sleepwalker = target instanceof NPC ? getSleepwalker((NPC) target) : null;
		if (sleepwalker == null || sleepwalker.isKilled())
		{
			return;
		}

		int cycles = Math.max(0, projectile.getRemainingCycles());
		markKilled(sleepwalker, (cycles + CYCLES_PER_TICK - 1) / CYCLES_PER_TICK);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (sleepwalkers.isEmpty() || !(event.getActor() instanceof NPC)
			|| event.getHitsplat().getAmount() <= 0)
		{
			return;
		}

		// Damage that has already landed is a kill with nothing left to predict, which is what covers melee
		Sleepwalker sleepwalker = getSleepwalker((NPC) event.getActor());
		if (sleepwalker != null)
		{
			markKilled(sleepwalker, 0);
		}
	}

	/**
	 * Whether she is taking it in, which is the one thing that keeps a sleepwalker on screen. One that
	 * reaches her merges into her and charges the power blast, and that is the thing you still need to
	 * see coming, so a sleepwalker playing the absorb is never hidden however dead it is.
	 */
	private boolean isEntering(Sleepwalker sleepwalker)
	{
		return sleepwalker.getNpc().getAnimation() == AnimationID.SLEEPWALKER_ABSORB;
	}

	/**
	 * Hides it, and sets the tick by which it has to be dead for the call to have been right. A tick of
	 * grace goes on top of the hit so that a hitsplat landing on the tick it was due doesn't flicker.
	 * <p>
	 * Every way a kill is spotted comes through here, so the one sleepwalker that is never hidden is
	 * never hidden by any of them.
	 */
	private void markKilled(Sleepwalker sleepwalker, int landsIn)
	{
		if (isEntering(sleepwalker))
		{
			return;
		}

		int deadline = landsIn + 1;
		if (sleepwalker.isKilled())
		{
			// A closer read of the same kill, which only ever brings the deadline in
			sleepwalker.setDeadlineTicks(Math.min(sleepwalker.getDeadlineTicks(), deadline));
			return;
		}

		sleepwalker.setHiddenTicks(0);
		sleepwalker.setDeadlineTicks(deadline);
		if (hideKilled)
		{
			// Lets other plugins, like NPC indicators, treat it as dead too
			sleepwalker.getNpc().setDead(true);
		}

		if (fakeXpDrops)
		{
			dropXp();
		}
	}

	/**
	 * A drop of the game's own, run the way the XP drop plugin runs the one it fakes. It comes up where
	 * your drops always come up, in the size, colour and speed you set in game, and it goes out as a
	 * {@link net.runelite.api.events.FakeXpDrop} on the way, which is what plugins that draw their own
	 * drops, Customizable XP drops among them, read it off.
	 * <p>
	 * The sleepwalker is hidden before the game has said anything about the hit, so this stands in for
	 * the real drop that is still a tick or more away.
	 */
	private void dropXp()
	{
		client.runScript(ScriptID.XPDROP_DISABLED, Skill.HITPOINTS.ordinal(), HITPOINTS_XP);
	}

	private void track(NPC npc)
	{
		if (npc.getId() != NpcID.NIGHTMARE_CHALLENGE_SLEEPWALKER || getSleepwalker(npc) != null)
		{
			return;
		}

		sleepwalkers.add(new Sleepwalker(npc));
	}

	private void show(Sleepwalker sleepwalker)
	{
		sleepwalker.setHiddenTicks(-1);
		sleepwalker.getNpc().setDead(false);
	}

	private Sleepwalker getSleepwalker(NPC npc)
	{
		for (int i = 0; i < sleepwalkers.size(); i++)
		{
			Sleepwalker sleepwalker = sleepwalkers.get(i);
			if (sleepwalker.getNpc() == npc)
			{
				return sleepwalker;
			}
		}

		return null;
	}

	List<Sleepwalker> getSleepwalkers()
	{
		return sleepwalkers;
	}

	/**
	 * Every renderable the client is about to draw comes through here, so nothing is looked up until
	 * there is something to hide.
	 */
	private boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		if (!hideKilled || sleepwalkers.isEmpty() || !(renderable instanceof NPC))
		{
			return true;
		}

		Sleepwalker sleepwalker = getSleepwalker((NPC) renderable);
		return sleepwalker == null || !sleepwalker.isKilled();
	}

	private void loadConfig()
	{
		hideKilled = config.hideKilled();
		fakeXpDrops = config.fakeXpDrops();
	}

	/**
	 * Every sleepwalker already in the scene, so turning the plugin on part way through a fight picks
	 * them up rather than waiting for the next spawn.
	 */
	private void rescan()
	{
		reset();
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		for (NPC npc : client.getNpcs())
		{
			track(npc);
		}
	}

	private void reset()
	{
		for (int i = 0; i < sleepwalkers.size(); i++)
		{
			Sleepwalker sleepwalker = sleepwalkers.get(i);
			if (sleepwalker.isKilled())
			{
				sleepwalker.getNpc().setDead(false);
			}
		}
		sleepwalkers.clear();
		swingTick = -1;
		lastAnim = -1;
		lastAnimFrame = 0;
	}
}
