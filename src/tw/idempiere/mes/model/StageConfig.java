package tw.idempiere.mes.model;

/**
 * Centralized configuration for production stages.
 * Ensures consistency between Timeline and KPI Dialog displays.
 * 
 * @author Gemini AI
 * @version 1.0
 */
public enum StageConfig {
    CUTTING("Cutting", "✂️", "#f44336"), // Red
    SEWING("Sewing", "🧵", "#9c27b0"), // Purple
    PACKING("Packing", "📦", "#ff9800"), // Orange
    MATERIAL_ISSUE("Material Issue", "🧱", "#2196f3"), // Blue
    UNKNOWN("Unknown", "❓", "#6c757d"); // Gray

    private final String label;
    private final String icon;
    private final String color;

    /**
     * Constructor for StageConfig enum.
     * 
     * @param label Display name of the stage
     * @param icon  Emoji icon for the stage
     * @param color Hex color code for the stage badge
     */
    StageConfig(String label, String icon, String color) {
        this.label = label;
        this.icon = icon;
        this.color = color;
    }

    /**
     * Gets the display label for this stage.
     * 
     * @return Stage label (e.g., "Cutting", "Sewing")
     */
    public String getLabel() {
        return label;
    }

    /**
     * Gets the emoji icon for this stage.
     * 
     * @return Emoji icon (e.g., "✂️", "🧵")
     */
    public String getIcon() {
        return icon;
    }

    /**
     * Gets the hex color code for this stage.
     * 
     * @return Color code (e.g., "#f44336")
     */
    public String getColor() {
        return color;
    }

    /**
     * Finds stage configuration by name (case-insensitive).
     * 
     * @param name Stage name to search for
     * @return Matching StageConfig, or UNKNOWN if not found
     */
    public static StageConfig fromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return UNKNOWN;
        }

        String trimmedName = name.trim();
        for (StageConfig stage : values()) {
            if (stage.label.equalsIgnoreCase(trimmedName)) {
                return stage;
            }
        }

        return UNKNOWN;
    }
}
