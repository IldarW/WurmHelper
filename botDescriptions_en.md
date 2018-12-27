# Bot types

## ar - ArcherBot

Automatically shoots at selected target with currently equipped bow. When the string breaks tries to place a new one. Deactivates on target death.

### Commands

1) s [threshold] - Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold
2) string - String the current bow with a string.

## a - AssistantBot

Assists player in various ways.

### Commands

1) w - Toggle automatic drinking of the liquid the user pointing at.
2) wid [id] - Toggle automatic drinking of liquid with provided id.
3) ls - Show the list of available spells for autocasting.
4) c [spell_abbreviation] - Toggle automatic casts of spells (if player has enough favor). Provide an optional spell abbreviation to change the default Dispel spell. You can see the list of available spell with "ls" key.
5) p - Toggle automatic praying. The timeout between prayers can be configured separately.
6) pt [timeout] - Change the timeout (in milliseconds) between prayers.
7) pid [id] - Toggle automatic praying on altar with provided id.
8) s - Toggle automatic sacrificing. The timeout between sacrifices can be configured separately.
9) st [timeout] - Change the timeout (in milliseconds) between sacrifices.
10) sid [id] - Toggle automatic sacrifices at altar with provided id.
11) kb - Toggle automatic burning of kindlings in player's inventory. AssistantBot will combine the kindlings and burn them using selected forge. The timeout of burns can be configured separately.
12) kbt [timeout] - Change the timeout (in milliseconds) between kingling burns.
13) kbid [id] - Toggle automatic kindling burns at forge with provided id.
14) cwov - Toggle automatic casts of Wysdom of Vynora spell.
15) cleanup - Toggle automatic trash cleanings. The timeout between cleanings can be configured separately.
16) cleanupt [timeout] - Change the timeout (in milliseconds) between trash cleanings.
17) cleanupid [id] - Toggle automatic cleaning of items inside trash bin with provided id.
18) l- Toggle automatic lockpicking. The target chest should be beneath the user's mouse.
19) lt [timeout] - Change the timeout (in milliseconds) between lockpickings.
20) lid [id] - Toggle automatic lockpicking of target chest with provided id.
21) v - Toggle verbose mode. In verbose mode the AssistantBot will output additional info to the console.

## big - BulkItemGetterBot

Automatically transfers items to player's inventory from configured bulk storages. The n-th  source item will be transferred to the n-th target item.

### Commands

1) as - Add the source (item in bulk storage) the user is currenly pointing to.
2) at - Add the target item the user is currently pointing to.
3) asid [id] - Add the source (item in bulk storage) with provided id.
4) atid [id] - Add the target item with provided id.
5) ssxy - Add source item from fixed point on screen.

## ch - ChopperBot

Automatically chops felled trees near player.

### Commands

1) s [threshold] - Set the stamina threshold (float value between 0 and 1). Player will not do any actions if his stamina is lower than specified threshold.
2) d [distance] - Set the distance (in meters, 1 tile is 4 meters) the bot should look around player in search for a felled tree.
3) c [amount] - Set the amount of chops the bot will do each time.
4) area [tiles_ahead] [tiles_to_the_right] - Toggle the area processing mode.
5) area_speed [speed] Set the speed (float value) of moving for area mode. Default value is 1 second per tile.

## c - CrafterBot

Automatically does crafting operations using items from crafting window. New crafting operations are not starting until an action queue becomes empty. This behaviour can be disabled.

### Commands

1) r - Toggle the source item repairing (on the left side of crafting window). Usually it is an instrument. When the source item gets 10% damage player will repair it automatically.
2) st [target_name] - Set the target item name. CrafterBot will place item with provided name from your inventory to the target slot (on the right side of crafting window).
3) stxy - Set the target item fixed point. CrafterBot will place item from that fixed point of screen to the target item slot (on the right side of crafting window).
4) ss [source_name] - Set the source item name. CrafterBot will place item with provided name from your inventory to the source slot (on the left side of crafting window).
5) ssxy - Set the source item fixed point. CrafterBot will place item from that fixed point of screen to the source item slot (on the left side of crafting window).
6) nosort - Sorting of source and target items is enabled by default. This key toggles sorting on and off.
7) cs - Combine source items (on the left side of crafting window).
8) ct - Combine target items (on the right side of crafting window).
9) ctimeout [timeout] - Set the timeout (in milliseconds) for item combining.
10) s [threshold] - Set the stamina threshold (float value between 0 and 1). Player will not do any actions if his stamina is lower than specified threshold.
11) u - Toggle the special mode in which CrafterBot will place an item to the target item slot which is at the top of "Needed items" list.
12) ssid [id] - Set an item with provided id to the source slot(on the left side of crafting window.
13) an [number] - Set an action number. The number of crafting operations the player will do on each click on continue/create button.
14) noan - Toggles the check for action queue state before the start of each crafting operation. By default CrafterBot will check action queue and start crafting operations only when it is empty.
15) s1s - Toggles the setting of single item to source slot of crafting window.

## fp - FlowerPlanterBot

Skills up player's gardening skill by planting and picking flowers in surrounding area.

### Commands

1) s [threshold] - Set the stamina threshold (float value between 0 and 1). Player will not do any actions if his stamina is lower than specified threshold.

## i - ImproverBot.class

Improves selected items in provided inventories. Tools searched from player's inventory. Items like water or stone searched before each improve, actual instruments searched one time before improve of the first item that must be improved with this tool. Tool for improving is determined by improve icon that you see on the right side of item row in inventory. For example improve icons for stone chisel and carving knife are equal, and sometimes bot can choose wrong tool. Use "ci" key to change the chosen instrument.

### Commands

1) s [threshold] - Set the stamina threshold (float value between 0 and 1). Player will not do any actions if his stamina is lower than specified threshold.
2) at - Add new inventory (under mouse cursor). Selected items in this inventory will be improved.
3) ls - List available improving skills.
4) ss [skill_abbreviation] - Set the skill. Only tools from that skill will be used. You can list available skills using "ls" key.
5) g - Toggle the ground mode. Set the skill first by "ss" key.
6) ci - Change previously chosen instrument by tool selected in player's inventory.

## fsm - ForageStuffMoverBot

Moves foragable and botanizable items from your inventory to the target inventories. Optionally you can toggle the moving of rocks or rare items on and off.

### Commands

1) at - Add new target item. Foragable and botanizable items will be moved to that destination.
2) r - Toggle moving of rare items.
3) mr - Toggle moving of rocks.

## fr - ForesterBot

A forester bot. Can pick and plant sprouts, cut trees/bushes and gather the harvest in 3x3 area around player. Bot can be configured to process rectangular area of any size. Sprouts, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. Default container name is "backpack". Containers only in root directory of player's inventory will be taken into account. New item names can be added (harvested fruits for example) to be moved to containers too. Steppe and moss tiles will be cultivated if planting is enabled and player have shovel in his inventory.

### Commands

1) s [threshold] - Set the stamina threshold (float value between 0 and 1). Player will not do any actions if his stamina is lower than specified threshold.
2) ca - Toggle the cutting of sprouts from all trees.
3) cs - Toggle the cutting of shriveled trees.
4) df - Toggle the cutting of all trees (deforestation).
5) h - Toggle the harvesting.
6) p - Toggle the planting.
7) scn [container_name] - Set the new name for containers to put sprouts/harvest.
8) na [number] - Set the number of actions bot will do each time.
9) aim [item_name] - Add new item name for moving into containers.
10) area [tiles_ahead] [tiles_to_the_right] - Toggle the area processing mode.
11) area_speed [speed] - Set the speed (float value) of moving for area mode. Default value is 1 second per tile.

## fg - ForagerBot

Can forage, botanize, collect grass and flowers in an area surrounding player. Bot can be configured to process rectangular area of any size. Picked items, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. Default container name is "backpack". Containers only in root directory of player's inventory will be taken into account. Bot can be configured to drop picked items on the floor.

### Commands

1) s [threshold] - Set the stamina threshold (float value between 0 and 1). Player will not do any actions if his stamina is lower than specified threshold.
2) g - Toggle the grass gathering.
3) f - Toggle the foraging.
4) ftl - Show the list of foraging types.
5) ft [type] - Set the foraging type.
6) b - Toggle the botanizing.
7) btl - Show the list of botanizing types.
8) bt [type] - Set the botanizing type.
9) d - Toggle the dropping of collected items to the ground.
10) v - Toggle the verbose mode. Additional information will be shown in console during the work of the bot in verbose mode.
11) scn [container_name] - Set the new name for containers to put sprouts/harvest.
12) na [number] - Set the number of actions bot will do each time.
13) area [tiles_ahead] [tiles_to_the_right] - Toggle the area processing mode.
14) area_speed [speed] - Set the speed (float value) of moving for area mode. Default value is 1 second per tile.

## gig - GroundItemGetterBot

Collects items from the ground around player.

### Commands

1) d [distance] - Set the distance (in meters, 1 tile is 4 meters) the bot should look around player in search for items.
2) a [item_name] - Add new item name to search list.

## g - GuardBot

Looks for messages in Event and Combat tabs. Raises alarm if no messages were received during configured time. With no provided keywords the bot will be satisfied with every message. If user adds some keywords bot will compare messages only with them.

### Commands

1) at [timeout] - Set the alarm timeout (in milliseconds). Alarm will be raised if no valid messages was processed during that period.
2) a [keyword] - Add new keyword.
3) cs [path] - Set a path to a custom sound file for alarm. Use .wav file.
4) soundtest - Plays the alarm sound.

## im - ItemMoverBot

Moves items from your inventory to the target destination.

### Commands

1) st - Set the target item (under mouse pointer). Items from your inventory will be moved inside this item if it is a container or next to it otherwise.
2) stid [id] - Set the id of target item. Items from your inventory will be moved inside this item if it is a container or next to it otherwise.
3) str - Set the target container (under mouse pointer). Items from your inventory will be moved to the root directory of that container.
4) stcn [number] - Set the number of items to put inside each container. Use with "stc" key.
6) stc [container_name] - Set the target container (under mouse pointer) with another containers inside. Items from your inventory will be moved to containers with provided name. Bot will try to put 100 items inside each container. But you change this value using "stcn" key.
7) sw [weight] - Set the maximum weight (float number) for item to be moved. Affects the last added item name.
8) a [name] - Add new item name to move to the targets. The maximum weight of moved item can be configured with "sw" key.
9) r - Toggle the moving of rare items. Disabled by default.
10) fl - Toggle the moving of only first level items of your inventory. Items that match added keywords but lying inside a group or a container will not be touched. Enabled by default.

## m - MinerBot

Mines rocks and smelts ores.

### Commands

1) s [threshold] - Set the stamina threshold (float value between 0 and 1). Player will not do any actions if his stamina is lower than specified threshold.
2) c [amount] - Change the amount of clicks bot will do each time.
3) sc - Toggle the combining of shards lying around the player in piles.
4) scn [name] - Change the name of shards to combine. See "sc" key.
5) fixed - Set the fixed tile mining mode. Bot will remember selected tile and mine it.
6) st - Set the mining mode in which bot will mine currently selected tile.
7) area - Set the area mining mode in which bot will mine 3x3 area around player.
8) ft - Set the mining mode in which bot will mine a tile in front of a player.
9) o - Toggle the mining of ore tiles. Enabled by default.
10) m - Toggle the automatic moving forward when bot have no work.
11) sm - Toggle the smelting of ores in selected pile.
12) at [min_quality] - Add the target (under the mouse cursor) for lumps with provided minimum quality (0-100).
13) ati [min_quality] - Add the target inventory (under the mouse cursor) for lumps with provided minimum quality (0-100).
14) atid [id] [min_quality] - Add the target with provided id for lumps with provided minimum quality (0-100).
15) sp - Set a pile (under the mouse cursor) for smelting ores.
16) ssm - Set a smelter (under the mouse cursor) for smelting ores.
17) sft [timeout] - Set a smelter fuelling timeout for smelting ores (in milliseconds).
18) sfn [name] - Set a name for the fuel for smelting ores.
19) v - Toggle the verbose mode. While verbose bot will show additional info in console.

## md - MeditationBot

Meditates on the carpet. Assumes that there are no restrictions on meditation skill.

### Commands

1) s [threshold] - Set the stamina threshold (float value between 0 and 1). Player will not do any actions if his stamina is lower than specified threshold.
2) c [amount] - Change the amount of actions bot will do each time.
3) rt [timeout] - Set the meditation rug repair timeout (in milliseconds).

## h - HealingBot

Heals the player's wounds with cotton found in inventory.

### Commands

1) md [min_damage] - Set the minimum damage of the wound to be treated.

## f - FarmerBot

Tends the fields, plants the seeds, cultivates the ground, collects harvests.

### Commands

1) s [threshold] - Set the stamina threshold (float value between 0 and 1). Player will not do any actions if his stamina is lower than specified threshold.
2) r - Toggle the tool repairing.
3) ft - Toggle the farm tending.
4) h - Toggle the harvesting.
5) p [seeds_name] - Toggle the planting. Provide the name of the seeds to plant.
6) c - Toggle the dirt cultivation.
7) and [name] - Add new item name to drop on the ground.
8) d - Toggle the dropping of harvested items. Add item names to drop by "and" key.
9) dl [number] - Set the drop limit, configured number of harvests won't be dropped.
10) area [tiles_ahead] [tiles_to_the_right] - Toggle the area processing mode.
11) area_speed [speed] - Set the speed (float value) of moving for area mode. Default value is 1 second per tile.

## d - DiggerBot

Does the dirty job for you.

### Commands

1) s [threshold] - Set the stamina threshold (float value between 0 and 1). Player will not do any actions if his stamina is lower than specified threshold.
2) d [height] - Toggle the digging until the specified height is reached.
3) dtp - Toogle the use of "Dig to pile" action.
4) dtile [height] - Toggle the digging until the specified height is reached on all 4 corners of current tile.
5) c [amount] - Set the amount of actions the bot will do each time.
6) l - Toggle the levelling of selected tile.
7) la [height] - Toggle the levelling of area around player.
8) tr - Toggle the repairing of the tool.
9) sm - Toggle the surface mining. The bot will do the same but with the pickaxe on the rock.
10) area [tiles_ahead] [tiles_to_the_right] - Toggle the area processing mode.
11) area_speed [speed] - Set the speed (float value) of moving for area mode. Default value is 1 second per tile.

## pc - PileCollector

Collects piles of items to bulk containers. Default name for target items is "dirt".

### Commands

1) stn [name] - Set the name for target items. Default name is "dirt".
2) st [name] - Set the target bulk inventory to put items to. Provide an optional name of containers inside inventory. Default is "large crate".
3) stcc [capacity] - Set the capacity (integer value) for target container. Default value is 300.

## fsh - FisherBot

Catches and cuts fish.

### Commands

1) r - Toggle the rod repairing.
2) line - Toggle a fishing line on the current rod replacing.

## pr - ProspectorBot

Prospect selected tile.

### Commands

1) s [threshold] - Set the stamina threshold (float value between 0 and 1). Player will not do any actions if his stamina is lower than specified threshold.
5) c [amount] - Set the amount of actions the bot will do each time.
