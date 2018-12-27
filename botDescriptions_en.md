# Bot types

## ar - ArcherBot

Automatically shoots at selected target with currently equipped bow. When the string breaks tries to place a new one. Deactivates on target death.

## a - AssistantBot

Assists player in various ways.

## big - BulkItemGetterBot

Automatically transfers items to player's inventory from configured bulk storages. The n-th  source item will be transferred to the n-th target item.

## ch - ChopperBot

Automatically chops felled trees near player.

## c - CrafterBot

Automatically does crafting operations using items from crafting window. New crafting operations are not starting until an action queue becomes empty. This behaviour can be disabled.

## fp - FlowerPlanterBot

Skills up player's gardening skill by planting and picking flowers in surrounding area.

## i - ImproverBot.class
Improves selected items in provided inventories. Tools searched from player's inventory. Items like water or stone searched before each improve, actual instruments searched one time before improve of the first item that must be improved with this tool. Tool for improving is determined by improve icon that you see on the right side of item row in inventory. For example improve icons for stone chisel and carving knife are equal, and sometimes bot can choose wrong tool. Use "ci" key to change the chosen instrument.

## fsm - ForageStuffMoverBot

Moves foragable and botanizable items from your inventory to the target inventories. Optionally you can toggle the moving of rocks or rare items on and off.

## fr - ForesterBot

A forester bot. Can pick and plant sprouts, cut trees/bushes and gather the harvest in 3x3 area around player. Bot can be configured to process rectangular area of any size. Sprouts, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. Default container name is "backpack". Containers only in root directory of player's inventory will be taken into account. New item names can be added (harvested fruits for example) to be moved to containers too. Steppe and moss tiles will be cultivated if planting is enabled and player have shovel in his inventory.

## fg - ForagerBot

Can forage, botanize, collect grass and flowers in an area surrounding player. Bot can be configured to process rectangular area of any size. Picked items, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. Default container name is "backpack". Containers only in root directory of player's inventory will be taken into account. Bot can be configured to drop picked items on the floor.

## gig - GroundItemGetterBot

Collects items from the ground around player.

## g - GuardBot

Looks for messages in Event and Combat tabs. Raises alarm if no messages were received during configured time. With no provided keywords the bot will be satisfied with every message. If user adds some keywords bot will compare messages only with them.

## im - ItemMoverBot

Moves items from your inventory to the target destination.

## m - MinerBot

Mines rocks and smelts ores.

## md - MeditationBot

Meditates on the carpet. Assumes that there are no restrictions on meditation skill.

## h - HealingBot

Heals the player's wounds with cotton found in inventory.

## f - FarmerBot

Tends the fields, plants the seeds, cultivates the ground, collects harvests.

## d - DiggerBot

Does the dirty job for you.

## pc - PileCollector

Collects piles of items to bulk containers. Default name for target items is "dirt".

## fsh - FisherBot

Catches and cuts fish.

## pr - ProspectorBot

Prospect selected tile.
