# Chart Selection Guide

This document explains how the backend selects chart types for different query patterns.

## Chart Type Selection Rules

### Bar Chart
**Best for:**
- Ranking queries ("top 5", "bottom 10", "highest", "best")
- Comparison between discrete entities
- Showing magnitude differences
- 2-50 items being compared

**Selection Criteria:**
- Query intent score for "bar" > 1.5 OR primary intent is "comparison"
- 2-4 columns (1 categorical + 1-2 numeric)
- 2-50 rows
- Has numeric columns for y-axis

**Examples:**
- "top 5 players based on points" → Bar Chart
- "compare performance between Jordan Howard and Trevon Bluiett" → Bar Chart
- "highest scoring players" → Bar Chart

### Line Chart
**Best for:**
- Temporal data ("over time", "by season", "trend")
- Sequential/ordered data
- Showing progression or change
- Continuous data on x-axis

**Selection Criteria:**
- Query intent score for "line" > 1.5 OR primary intent is "trend"
- Has temporal column (date, season, year, month, day)
- At least one numeric metric
- 3-100 data points

**Examples:**
- "comparing performance between players in seasons 2015 to 2017" → Line Chart
- "trend over years" → Line Chart
- "points scored by season" → Line Chart

### Pie Chart
**Best for:**
- Part-to-whole relationships ("distribution of", "percentage", "share")
- 2-8 categories only (too many makes it unreadable)
- All positive values that sum to meaningful total
- Explicit distribution requests

**Selection Criteria:**
- Query intent score for "pie" > 2.0 (STRONG signal required)
- Exactly 2 columns (1 categorical + 1 numeric)
- 2-8 rows
- All numeric values are positive

**Examples:**
- "distribution of points scored in different ways" → Pie Chart
- "percentage breakdown of scoring types" → Pie Chart
- "share of total points" → Pie Chart

### Bubble Chart
**Best for:**
- 3+ metrics per entity
- Correlation analysis
- Multi-dimensional comparisons
- Scatter plots with size dimension

**Selection Criteria:**
- Query intent score for "bubble" > 0.5 OR primary intent is "correlation"
- At least 3 columns
- 2+ numeric columns
- 5+ rows for meaningful visualization

**Examples:**
- "relationship between points, rebounds, and assists" → Bubble Chart
- "correlation between multiple metrics" → Bubble Chart

### Table
**Best for:**
- Explicit table requests ("show tabular", "list all")
- Many columns (5+)
- Detailed data inspection
- No clear visualization pattern

**Selection Criteria:**
- Explicit "table" request OR
- 5+ columns OR
- No other chart type matches

**Examples:**
- "show the tabular data for all wins" → Table
- Queries returning many columns → Table

## Scoring System

The system uses a weighted scoring approach:

1. **Explicit Request** (weight: 100.0) - User explicitly asks for chart type
2. **SQL Patterns** (weight: 10.0) - Detected from SQL structure
3. **Result Structure** (weight: 5.0) - Based on actual data characteristics
4. **Pattern Detection** (weight: 3.0) - Regex-based pattern matching
5. **Keyword Matching** (weight: 1.0) - Simple keyword presence

## Pattern Detection

The `QueryPatternDetector` identifies common patterns:

- **Ranking**: "top N", "bottom N", "highest", "lowest", "best", "worst"
- **Temporal**: "over time", "by season", "from X to Y", "trend"
- **Distribution**: "distribution of", "breakdown", "percentage", "proportion"
- **Comparison**: "compare", "versus", "vs"

## SQL Pattern Hints

The system analyzes SQL to provide additional hints:

- **Ranking**: `LIMIT` + `ORDER BY DESC/ASC`
- **Temporal**: `GROUP BY` with temporal columns (season, year, date)
- **Distribution**: Aggregations (`SUM`, `COUNT`) with few `GROUP BY` columns

## Debugging Chart Selection

Check logs for:
- `Query analyzed:` - Shows intent scores and primary intent
- `Chart selection:` - Shows final chart selection with reasoning
- `*Transformer: * match` - Shows why each transformer accepted/rejected

## Requesting Specific Chart Types

Users can explicitly request chart types:
- "show as bar chart"
- "generate a line chart"
- "display as pie chart"
- "create a bubble chart"
- "show tabular data"

Explicit requests override all other signals.

