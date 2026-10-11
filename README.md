# Instant Sleepwalkers
Hides each sleepwalker at Phosani's Nightmare the moment you kill it, before the projectile is in the air and the hitsplat lands, so you can move straight on to the next one. A sleepwalker has 10 hitpoints and -100 in every defence bonus, so a hit on one hardly ever misses and any damage kills it.

The one she is taking in is never hidden. A sleepwalker that reaches her merges into her and charges the power blast, so one playing the absorb stays on screen however dead it is, and one that starts the absorb while hidden comes straight back.

## How a kill is spotted
- **Your swing.** Swinging at one is the kill, with no XP drop to wait on. The animation is the earliest the attack shows up anywhere, a tick or more before the projectile.
- **The animation frame.** The game only sends an animation when it is a different one, so a second attack in a row with the same weapon never arrives as a change. It restarts the animation instead, which the frame gives away at the end of the tick.
- **The projectile.** A closer read of the same kill, and the one way a thrall's shot is spotted.
- **Landed hitsplats.** Damage that has already landed, which catches anything the rest missed.
- **Wrong guesses.** Hiding one sets the tick it has to be dead by, taken from how far away the hit came from, 5 ticks at the very most. Still standing past that and it goes back on screen.

## Settings
**Hide killed sleepwalkers**: hides a sleepwalker the moment it is hit. Other plugins, like NPC Indicators, treat it as dead too.

**Fake XP drops**: puts the game's own hitpoints XP drop up on each kill, in the position, size, colour and speed you have set in game. Every kill is the same 13 XP. It goes out as a fake XP drop the way the XP drop plugin's does, so Customizable XP Drops and the like draw it in their own style.

**Highlight**
- **Highlight sleepwalkers**: highlights sleepwalkers like NPC Indicators. Only ones that are still coming are highlighted.
- **Mode**: true tile, tile, hull or outline. True tile shows the tile the server has the sleepwalker on, not where it is drawn mid-walk.
- **Color**: the color of the highlight.
