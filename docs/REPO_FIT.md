# Repos from the two screens, and what they are worth here

Checked against this Android app. None of them is a library this phone can import and run.

| Repo | What it actually is | Use here |
|---|---|---|
| [public-apis/public-apis](https://github.com/public-apis/public-apis) | A list of free HTTP APIs | A catalogue to pick from later. Not code. The app already calls Wikipedia, Wikidata, DuckDuckGo, Reddit and Wikimedia Commons itself. |
| [thedotmack/claude-mem](https://github.com/thedotmack/claude-mem) | A Claude Code plugin that compresses a coding session so the next session can resume | Desktop only. The idea that matters is memory that survives a new chat. That is now `ChatBook` on the phone. |
| [HKUDS/LightRAG](https://github.com/HKUDS/LightRAG) | A Python graph-retrieval server | Does not run inside this app. A graph of facts would be a later server, not a class dropped into `MainActivity`. |
| [kepano/obsidian-skills](https://github.com/kepano/obsidian-skills) | Agent skills that teach a desktop coding agent how Obsidian's notes work | Not a phone UI. |
| [affaan-m/everything-claude-code](https://github.com/affaan-m/everything-claude-code) | A harness of skills, hooks and commands for Claude Code | Same. It does not render a chat, a picture or a video on Android. |
| [obra/superpowers](https://github.com/obra/superpowers) | A Claude Code workflow: brainstorm, spec, plan, test, then code | A way of working for a coding agent. Not a widget. |
| [gsd-build/get-shit-done](https://github.com/gsd-build/get-shit-done) | Another Claude Code planning workflow | Same. |
| [czlonkowski/n8n-mcp](https://github.com/czlonkowski/n8n-mcp) | An MCP server so a desktop agent can drive n8n | Needs a running n8n. Not this app. |
| The "UI UX" line in the second still | The address was cut off | Not identified, so nothing from it was copied. |

What was built from that list is the part that belongs on the phone: separate chat sections, a persona on each section, and one shared memory those sections can all read. Code is shown as code, a fetched photograph is an image, and a video view plays only a file that is already on the phone.
