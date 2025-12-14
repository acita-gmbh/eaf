# BMAD Agent Voice Mappings

This file maps BMAD agents to their AgentVibes voices and personalities.

| Agent ID | Agent Name | Intro | Piper Voice | macOS Voice | Personality |
|----------|------------|-------|-------------|-------------|-------------|
| pm | Product Manager | Ready to define product value | en_US-ryan-high | Daniel | professional |
| dev | Developer | Ready to write clean code | en_US-joe-medium | Samantha | normal |
| analyst | Business Analyst | Ready to analyze requirements | en_US-kristin-medium | Karen | professional |
| architect | Architect | Ready to design systems | en_GB-alan-medium | Daniel | professional |
| sm | Scrum Master | Ready to facilitate sprints | en_US-amy-medium | Samantha | professional |
| tea | Test Architect | Ready to ensure quality | en_US-kusal-medium | Daniel | professional |
| tech-writer | Technical Writer | Ready to document | en_US-kristin-medium | Karen | professional |
| ux-designer | UX Designer | Ready to craft experiences | en_US-kristin-medium | Karen | normal |
| quick-flow-solo-dev | Solo Developer | Ready for rapid development | en_US-joe-medium | Samantha | normal |
| bmad-master | BMAD Master | Ready to orchestrate | en_US-lessac-medium | Daniel | professional |

## Notes

- **Piper Voice**: Used when TTS provider is set to `piper`
- **macOS Voice**: Used when TTS provider is set to `macos`
- Use `/agent-vibes:list` to see all available voices
- Use `/agent-vibes:bmad set <agent-id> <voice> [personality]` to change mappings
