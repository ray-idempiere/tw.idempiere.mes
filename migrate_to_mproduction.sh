#!/bin/bash
# Migration script: Replace PP_Order with M_Production in WProductionSchedule.java

FILE="/Users/ray/sources/tw.idempiere.mes/src/tw/idempiere/mes/form/WProductionSchedule.java"

# Backup original file
cp "$FILE" "$FILE.backup"

# Replace table name
sed -i '' 's/FROM PP_Order/FROM M_Production/g' "$FILE"
sed -i '' 's/PP_Order WHERE/M_Production WHERE/g' "$FILE"
sed -i '' 's/PP_Order SET/M_Production SET/g' "$FILE"

# Replace column names
sed -i '' 's/PP_Order_ID/M_Production_ID/g' "$FILE"
sed -i '' 's/QtyOrdered/ProductionQty/g' "$FILE"
sed -i '' 's/QtyDelivered/ProductionQty/g' "$FILE"  # M_Production uses single qty

# Replace in comments and strings
sed -i '' 's/"PP_Order"/"M_Production"/g' "$FILE"
sed -i '' 's/PP_Order not/M_Production not/g' "$FILE"

echo "Migration complete. Backup saved to $FILE.backup"
echo "Please review the changes and test thoroughly."
