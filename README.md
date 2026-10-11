# Instant Sleepwalkers
Swarmer for the sleepwalkers at Phosani's Nightmare. A sleepwalker has 10 hitpoints, no defence and -100 in every defence bonus, so a hit on one always lands and always kills. A sleepwalker is hidden the moment you swing at it, before the projectile is in the air and the hitsplat lands, so you can move straight on to the next one.

One sleepwalker is never hidden: the one she is taking in. A sleepwalker that reaches her merges into her and charges the power blast, and that is the thing you still need to see coming, so a sleepwalker playing the absorb animation is never hidden however dead it is, and one that starts it while hidden comes straight back.

The npc is killed locally, the same way Swarmer does it, so other plugins like NPC Indicators treat it as dead too.

## How a kill is spotted
- **Your swing.** A sleepwalker dies to any damage, so swinging at one is the kill and there is no waiting on an XP drop. Sleepwalkers give almost no XP, and nothing about the drop says which one it came from. The animation is the earliest the attack shows up anywhere, a tick or more before the projectile is in the air, which is what makes the sleepwalker go instantly.
- **The projectile.** A closer read of the same kill, and the only way a thrall is spotted, since that is a kill of yours you never animate for.
- **The end of the tick.** The game only sends an animation when it is a different one to the one already playing, so the second and every later attack in a row with the same weapon never arrives as an animation change. Such a swing is read off the animation frame instead, which drops back to the start when an attack restarts its own animation. This also covers a swing arriving before the target it was aimed at, since who you are interacting with and what you are animating are two separate things the server sends in no fixed order. Either way it is still the tick the swing was sent on.
- **Landed hitsplats.** Damage that has already landed, which catches anything the others missed.
- **Wrong guesses.** Hiding one sets the tick it has to be dead by, taken from how far away the hit came from. The slowest attack in the game over a distance is a spell, so that is the ceiling: a tick on the tile beside you, 2 out to 4 tiles, 3 to 7 and 4 to 10. Still standing past that and it goes back on screen.

## The numbers
For reference, the hit delays the deadline is built on, as the wiki gives them. Distance is counted edge to edge from your far side to the sleepwalker's near side.

| Weapon | Ticks | At 1-10 tiles |
| --- | --- | --- |
| Melee | 0 | 0 |
| Bows, crossbows | 1 + (3 + distance) / 6 | 1 at 1 tile, 2 out to 8, 3 past that |
| Knives, darts, thrownaxes, blowpipe, chinchompas | 1 + distance / 6 | 1 out to 5, 2 past that |
| Spells, powered staves | 1 + (1 + distance) / 3 | 1 at 1 tile, 2 out to 4, 3 to 7, 4 to 10 |
| Ballista | - | 2 out to 4, 3 past that |
| Armadyl and Zaryte crossbow specials, Tonalztics of Ralos | - | 2 |
| Skeletal thralls | - | 1 out to 4, 2 past that |
| Ghostly thralls | - | 0 out to 6, 1 past that |

Hits on an NPC land a tick later than the table says, because NPCs act before players. The deadline is given that tick on top.

## Settings
**Hide killed sleepwalkers**: hides a sleepwalker the moment it is hit. Other plugins, like NPC Indicators, also treat it as dead.

**Fake XP drops**: puts a hitpoints XP drop up on each kill, the game's own drop, in the position and the size, colour and speed you have set in game. It goes out as a fake XP drop the same way the XP drop plugin's does, so plugins that draw their own, Customizable XP drops among them, pick it up and draw it in their style. A sleepwalker has 10 hitpoints and dies to any damage, and damage pays 1 1/3 hitpoints experience a point, so every kill is the same 13. The sleepwalker is hidden before the game has said anything about the hit, so the drop stands in for the one that is still a tick or more away.

**Highlight**
- **Highlight sleepwalkers**: highlights sleepwalkers like NPC Indicators. Only ones that are still coming are highlighted.
- **Mode**: true tile, tile, hull or outline. True tile shows the tile the server has the sleepwalker on, not where it is drawn mid-walk.
- **Color**: the color of the highlight.
