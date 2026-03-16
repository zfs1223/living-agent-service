---
name: weather
description: "Get current weather and forecasts via wttr.in or Open-Meteo. Use when: user asks about weather, temperature, or forecasts for any location. NOT for: historical weather data, severe weather alerts, or detailed meteorological analysis. No API key needed."
homepage: https://wttr.in/:help
metadata:
  {
    "living-agent":
      { "emoji": "🌤️", "category": "core", "requires": { "tools": ["weather"] } },
  }
---

# Weather Skill

Get current weather conditions and forecasts.

## When to Use

✅ **USE this skill when:**

- "What's the weather?"
- "Will it rain today/tomorrow?"
- "Temperature in [city]"
- "Weather forecast for the week"
- Travel planning weather checks

## When NOT to Use

❌ **DON'T use this skill when:**

- Historical weather data → use weather archives/APIs
- Climate analysis or trends → use specialized data sources
- Hyper-local microclimate data → use local sensors
- Severe weather alerts → check official NWS sources
- Aviation/marine weather → use specialized services (METAR, etc.)

## Tool Usage

Use the `weather` tool with these parameters:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| location | string | Yes | City name, region, or airport code |
| format | string | No | Output format: simple, detailed, json, forecast |
| days | integer | No | Forecast days (0-3) |
| lang | string | No | Language code (zh-CN, en, etc.) |

## Examples

### Current Weather (Simple)

```json
{
  "location": "Beijing",
  "format": "simple"
}
```

Output: `Beijing: ⛅ +15°C`

### Detailed Weather

```json
{
  "location": "Shanghai",
  "format": "detailed"
}
```

### Weather Forecast

```json
{
  "location": "Tokyo",
  "format": "forecast",
  "days": 3
}
```

### JSON Format (for parsing)

```json
{
  "location": "New York",
  "format": "json"
}
```

## Location Formats

- City name: `Beijing`, `New York`, `London`
- Airport code: `PEK`, `JFK`, `LHR`
- Coordinates: `39.9,116.4`
- IP-based: leave empty for current location

## Notes

- No API key needed (uses wttr.in)
- Rate limited; don't spam requests
- Works for most global cities
- Supports Chinese: `lang=zh-CN`
