# Runs on /reload and server start.
scoreboard objectives add datastack.count dummy
tellraw @a[tag=datastack.debug] {"text":"[DataStack] loaded","color":"green"}
