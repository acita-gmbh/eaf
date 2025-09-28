#!/bin/bash
# Check for duplicate Spring beans in widget-demo

echo "Checking for duplicate @Component, @Service, @EventHandler, @QueryHandler beans..."

# Find all @Component beans
echo "=== @Component beans ==="
rg "@Component" products/widget-demo/src/main/kotlin --type kotlin -A 1 | grep "^class" | sort

echo ""
echo "=== @EventHandler beans ==="
rg "@EventHandler" products/widget-demo/src/main/kotlin --type kotlin -B 2 | grep "fun " | sort

echo ""
echo "=== @QueryHandler beans ==="
rg "@QueryHandler" products/widget-demo/src/main/kotlin --type kotlin -B 2 | grep "fun " | sort

echo ""
echo "=== @Aggregate beans ==="
rg "@Aggregate" products/widget-demo/src/main/kotlin --type kotlin -A 1 | grep "^class" | sort

echo ""
echo "Analysis: Each class/method should appear only once"
