# MES Production Schedule - User Guide

## Introduction
The MES Production Schedule is Ihre command center for production planning and monitoring. This guide explains how to navigate the interface and interpret the production KPIs.

## 1. Navigating the Schedule View
- **Timeline Bars**: Horizontal bars represent Production Orders scheduled for specific resources (machines).
- **Colors**:
  - **Blue/Standard**: In-progress or Scheduled orders.
  - **Green**: Completed orders.
  - **Red**: At-risk or Overdue orders.
- **Date Filtering**: Use the date selector in the top-right to view schedules for specific days.

## 2. Viewing Resource KPIs
To understand how a machine is performing for the day:
1.  **Click on any resource name or bar** in the timeline.
2.  A **Detailed KPI Dialog** will appear.

### The KPI Dialog Layout
The dialog is designed for high visibility on shop floor screens:
- **Left Column**: Displays the **Product Image**. This helps operators visually confirm they are working on the correct item.
- **Middle Column**: Displays the **Order Number**, **Product Name**, and **Product Code**.
- **Right Column**: Displays production progress:
  - **Target**: Total quantity ordered.
  - **Delivered**: Quantity completed so far.
  - **Completion Rate (%)**: A large color-coded percentage (Green if 100% or above, Red if below).

## 3. Best Practices
- **Images**: To ensure images show up in the KPI view, upload a thumbnail to the **Product window** in iDempiere using the Attachment (paperclip) icon. The system will use the first attached image.
- **Centering**: If a machine only has one order assigned for the day, the KPI card will automatically center itself for better visibility on large monitors.
- **Scaling**: The schedule view is designed to fit your screen height. If you have many resources, you can scroll vertically to find the one you need.

## 4. Troubleshooting
- **No Image?**: Check if the product has an attachment of type image in the Master Data.
- **Missing Order?**: Ensure the Production Order has a scheduled start/end date and is assigned to the correct resource.
