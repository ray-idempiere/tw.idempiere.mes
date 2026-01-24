/**
 * MES Production Timeline Renderer
 * 
 * @param {String} containerId - The DOM ID of the container div
 * @param {Array} groupsData - Array of group objects
 * @param {Array} itemsData - Array of item objects
 * @param {Object} options - Configuration object for Vis.js
 */
window.renderMESTimeline = function (containerId, groupsData, itemsData, options) {
    setTimeout(function () {
        var container = document.getElementById(containerId);
        if (!container) {
            console.error("renderMESTimeline: Container not found " + containerId);
            return;
        }

        // Initialize DataSets
        var groups = new vis.DataSet(groupsData);
        var items = new vis.DataSet(itemsData);

        // Merge defaults with passed options if needed, or just use passed options
        // We ensure mandatory defaults here if strictly required, but Java generally provides them.

        // Destroy existing timeline if stored on the element (cleanup)
        if (container.visTimeline) {
            container.visTimeline.destroy();
        }

        // Create Timeline
        var timeline = new vis.Timeline(container, items, groups, options);

        // Store instance on DOM element for easy retrieval if needed, 
        // AND global variable as requested for 'Today' button
        container.visTimeline = timeline;
        window.currentVisTimeline = timeline;

        console.log("MES Timeline rendered on " + containerId);
    }, 200);
}
