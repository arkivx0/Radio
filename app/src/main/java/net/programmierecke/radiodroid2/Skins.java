package net.programmierecke.radiodroid2;

/** Radio fork: dark skin registry. Columns: label string, theme, dialog theme, accent, bg, surface, fg. */
public final class Skins {
    private Skins() {}
    public static final int[][] ALL = {
        {R.string.theme_omarchy, R.style.MyMaterialTheme_SkinOmarchy, R.style.DialogTheme_SkinOmarchy, 0xFF7aa2f7, 0xFF1a1b26, 0xFF24283b, 0xFFc0caf5},
        {R.string.theme_amoled, R.style.MyMaterialTheme_SkinAmoled, R.style.DialogTheme_SkinAmoled, 0xFF4fc3f7, 0xFF000000, 0xFF121212, 0xFFffffff},
        {R.string.theme_gruvbox, R.style.MyMaterialTheme_SkinGruvbox, R.style.DialogTheme_SkinGruvbox, 0xFFfabd2f, 0xFF282828, 0xFF3c3836, 0xFFebdbb2},
        {R.string.theme_catppuccin, R.style.MyMaterialTheme_SkinCatppuccin, R.style.DialogTheme_SkinCatppuccin, 0xFFcba6f7, 0xFF1e1e2e, 0xFF313244, 0xFFcdd6f4},
        {R.string.theme_nord, R.style.MyMaterialTheme_SkinNord, R.style.DialogTheme_SkinNord, 0xFF88c0d0, 0xFF2e3440, 0xFF3b4252, 0xFFeceff4},
        {R.string.theme_dracula, R.style.MyMaterialTheme_SkinDracula, R.style.DialogTheme_SkinDracula, 0xFFbd93f9, 0xFF282a36, 0xFF44475a, 0xFFf8f8f2},
        {R.string.theme_enclave, R.style.MyMaterialTheme_SkinEnclave, R.style.DialogTheme_SkinEnclave, 0xFF5f8fd6, 0xFF0b0f1a, 0xFF121a2b, 0xFFd6e2f0},
        {R.string.theme_terminal, R.style.MyMaterialTheme_SkinTerminal, R.style.DialogTheme_SkinTerminal, 0xFF7dff9a, 0xFF020a04, 0xFF06140a, 0xFF33ff66},
        {R.string.theme_vintage, R.style.MyMaterialTheme_SkinVintage, R.style.DialogTheme_SkinVintage, 0xFFffb347, 0xFF2a1a10, 0xFF3b2416, 0xFFf1e3c6},
    };
}
