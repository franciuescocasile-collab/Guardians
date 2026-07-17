# Le icone di Guardians

## Gli elmi dei guardiani (in questa cartella)
I PNG ritagliati e normalizzati (256×256, sfondo trasparente) usati nell'app:
`guardian_sentinella.png`, `guardian_guardiano.png`, `guardian_custode.png`,
`guardian_gendarme.png`, `guardian_vedetta.png`, `guardian_messaggero.png`,
`guardian_esattore.png`, `guardian_araldo.png`.

Sono COPIE: gli originali che l'app usa stanno in
`app/src/main/res/drawable/`. Se vuoi cambiarne uno, sostituisci il file lì
(stesso nome, minuscolo, senza spazi) e chiedi a Claude di ritagliarlo.

## I simboli delle card e dei pulsanti
Sono i **Material Symbols** di Google: vettoriali, inclusi nella libreria
dell'app (non sono file nel progetto). Li puoi sfogliare e scaricare tutti
(SVG o PNG) su: **https://fonts.google.com/icons**

Quelli usati oggi nell'app (cerca questi nomi sul sito):
| Dove | Nome icona |
|---|---|
| Nuovo guardiano | `shield` |
| Squadre | `groups` (e `folder` per le cartelle) |
| Congelamento | `ac_unit` (fiocco di neve) |
| Statistiche | `bar_chart` |
| Sonno / Sveglia | `bedtime`, `alarm` |
| Guide | `menu_book` |
| Notifiche / Novità | `notifications` |
| Batteria | `battery_charging_full` |
| Sigillo | `lock` |
| App escluse | `block` |
| Personalizzazione | `palette` |
| Impostazioni avanzate | `tune` |
| Viaggio | `flight` |
| Fiamma streak | `local_fire_department` |
| Trascina card (home) | `drag_handle` |
| Aggiungi | `add` |
| Profilo | `person` |
| Preferiti/Perché | `favorite` |

Per proporre un'icona NUOVA: scarica il PNG dal sito (o disegnane una come
gli elmi) e mettila in `app/src/main/res/drawable/` con un nome tipo
`icona_nome.png` — poi Claude la aggancia dove serve.
