# Configuration

Everything can be changed from the TV: press UP from the top row to reach ⚙ and open
**Personnaliser**, or long-press any card. Changes apply the next time the home screen shows.

## Personnaliser

| Section | Option | Values |
|---|---|---|
| Apparence | Taille des cartes | Petite, Moyenne, Grande |
| | Couleur d'accent | Braise, Ambre, Menthe, Ciel, Corail, Blanc |
| | Noms sous les cartes | Oui / Non |
| | Descriptions | Oui / Non |
| Fond d'écran | Source | Photo du jour (Bing), Reddit, Dégradé, Mon image |
| | Subreddit | any subreddit; only TV-shaped images (16:9 to 21:9) are kept |
| | Changer d'image | every 1, 3, 6 or 24 hours |
| | Assombrir | Léger, Moyen, Fort |
| Accueil | Rangées | show, hide and reorder rows |
| | Entrées | rename inputs, pick their icon |
| | Chaînes des applis | show any preview channel an app publishes |
| | Afficher ou masquer des applis | one checklist for apps and inputs |
| | Réorganiser les applis | OK picks an app up, left/right moves it, OK puts it down |
| Horloge et météo | Horloge 24 h, Date, Météo, Ville, Unités | |
| Veille | Mode ambiant | Jamais, 2, 5, 10 or 20 minutes |

## Long-press menus

- **App**: Déplacer, Renommer, Masquer, Infos sur l'appli.
- **Input**: Renommer, Changer l'icône, Masquer.
- **Show or video**: Ouvrir l'appli, Infos sur l'appli.

## Input icons

Inputs get an icon guessed from their name ("PS5", "Switch 2", "Serveur"…). Pick another in
the icon picker, which shows every choice as the card it will become. Brand marks are drawn in
white on the brand colour.

## Files

Couch keeps its settings in its external files directory, which ADB can read and write:

```
/sdcard/Android/data/cloud.samlo.couch/files/
├── config.json
├── wallpaper.jpg          # used when the wallpaper source is "Mon image"
└── icons/
    ├── hdmi1.png          # custom image for an input (key = its system name, letters and digits)
    └── <package>.png      # custom image for an app (key = package name)
```

Custom images are centre-cropped to the card's 16:9 shape.

## config.json

Every key is optional; missing keys use the default. The file is rewritten by Couch whenever a
setting changes, so edit it while the home screen is in the background.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `pinnedApps` | string[] | `[]` | App order (package names). Unlisted apps follow, alphabetically. |
| `hiddenApps` | string[] | vendor extras | Hidden apps (package names). |
| `hiddenInputs` | string[] | `[]` | Hidden inputs (keys like `hdmi3`). |
| `names` | object | `{}` | Custom names, by app package or input key. |
| `inputIcons` | object | `{}` | Input key → icon id (`ps5`, `xbox`, `switch2`, `server`…). |
| `sectionOrder` | string[] | `[]` | Row order: `apps`, `inputs`, `ch:<channel id>`. |
| `hiddenSections` | string[] | `[]` | Hidden rows. |
| `channels` | object | `{}` | Channel id → shown or not, overriding the stock launcher's choice. |
| `cardSize` | string | `medium` | `small`, `medium`, `large`. |
| `accent` | string | `#FF8A4C` | Accent colour (hex). |
| `wallpaper` | string | `daily` | `daily`, `reddit`, `gradient`, `custom`. |
| `subreddit` | string | `WidescreenWallpaper` | Subreddit for the `reddit` source. |
| `wallpaperHours` | number | `3` | Hours between Reddit images. |
| `dim` | string | `medium` | `light`, `medium`, `strong`. |
| `showLabels` | boolean | `false` | Names under cards. |
| `showDescriptions` | boolean | `true` | Synopsis in the hero area. |
| `clock24h` | boolean | `true` | 24-hour clock. |
| `showDate` | boolean | `true` | Date under the clock. |
| `weather` | boolean | `true` | Weather in the top bar. |
| `weatherCity` | string | `""` | City for the weather; empty locates by IP address. |
| `fahrenheit` | boolean | `false` | °F instead of °C. |
| `ambientMinutes` | number | `5` | Idle minutes before ambient mode; `0` turns it off. |

Back up or restore the whole setup by copying the directory:

```sh
adb pull /sdcard/Android/data/cloud.samlo.couch/files couch-backup
adb push couch-backup/. /sdcard/Android/data/cloud.samlo.couch/files/
```
