# Privacy Display+ icon assets

`reference.png` is the icon supplied for this project. `foreground-master.png` is the
selected transparent foreground edit created for the Android adaptive icon.
The pre-rounded tile and black outer corners are removed. The phone, shield,
crossed-out eye and plus identity and blue/cyan palette are retained.

## Android packaging

- Native full-bleed background: `res/drawable/ic_launcher_background.xml`.
- Foreground PNGs: 108/162/216/324/432 px, for mdpi through xxxhdpi.
- The foreground is centered within a 108 dp canvas. Its meaningful artwork is
  sized to 48 dp, with a measured maximum radius of about 31 dp so it remains inside
  the 66 dp adaptive-icon safe circle. Android applies its own launcher mask.
- `ic_brand.png` at each density is the foreground-only in-app mark, without
  launcher padding or a pre-rounded background.
- The themed monochrome layer is a simplified native vector of the same phone,
  shield, crossed eye and plus motif. A noisy generated monochrome candidate was
  rejected and is not packaged.
- Mechanical ImageMagick packaging discards alpha below 10% to remove almost
  invisible generation-margin noise, trims those margins, resizes, and adds
  transparent safe-zone padding. The master file is kept unchanged.

No raster-generation dependency is needed to run the app.

## Credits

The Help toolbar glyph is Google Material Icons `help_outline`, Apache-2.0.
See [THIRD-PARTY-NOTICES.md](../../THIRD-PARTY-NOTICES.md).
