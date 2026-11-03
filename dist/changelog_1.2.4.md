Dragons 1.2.4 - a much bigger config file

Nothing changes in normal play: every new option starts at the value the mod already used. The new settings are for modpack makers and servers. You'll find them in config/skywyrms.json, which gets the new keys the first time the game starts. Restart the game or server after editing the file.

Fixed
- A crash ("Ticking entity" in DragonBreedGoal) that could happen right after two dragons laid an egg, roughly every second time when they had to walk to each other first. Thanks to Donastria for the crash report!

Taming, food and breeding
- "tameItems": the items that tame a wild adult dragon, e.g. ["diamond", "emerald", "gold_ingot"]. Default: meat (fish also works for the Tide Wyrm).
- "tameChance": the chance that one item tames the dragon, from 0 to 1. Default 0.333 (about 1 in 3). Set it to 1.0 to tame on the first try.
- "foodItems": the items that heal a tamed dragon and make a hatchling grow faster. Default: meat.
- "breedItems": the items used for breeding. Default: golden apples.
- Item lists take item ids ("minecraft:diamond" or just "diamond") and item tags ("#minecraft:meat"). An empty list [] keeps the default.

Stronger or weaker dragons
- "healthMultiplier", "attackDamageMultiplier", "armorMultiplier", "walkSpeedMultiplier", "flySpeedMultiplier", "abilityDamageMultiplier".
- 1.0 is normal and 2.0 is double. Example: "healthMultiplier": 2.0 and "attackDamageMultiplier": 1.5, so an iron golem can no longer beat a dragon on its own.
- The multipliers scale each dragon's own inherited stats, so differences between bloodlines are kept.

Turning abilities off
- "abilities": { "fire_breath": true, "frost_breath": true, "lightning": true, "venom_spit": true, "void_blink": true, "tidal_jet": true }
- Set an ability to false to switch it off for every dragon: ridden dragons, wild dragons and the blood effect in hybrids.

See-through dragon while riding (suggested in the comments, thanks!)
- "riderSeeThrough": "off" (default), "first_person" or "always". While you ride, your own dragon is drawn see-through so it doesn't block your view. Only you see it that way; other players see the dragon normally.

Wing sounds
- "wingSoundVolume" and "wingSoundPitch": 1.0 is normal. A lower pitch sounds heavier, and a volume of 0 mutes the wing beats.

The file also has a "_help" section that explains every key.
