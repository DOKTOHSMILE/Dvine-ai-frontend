# DVINE app functions

Generated from `suit/FunctionCode.java` (`FunctionCode.document()`); a test keeps this file in step. Do not edit by hand.

Every model that drives DVINE (an API model, the offline model, DVINE 1) controls the app the same way: it writes one function code alone on the first line of its reply, then a short sentence for the user. DVINE carries the function out and shows the result. The user's own words never run a function; a chat's tools (the tools button) decide which codes that chat may use.

Accepted forms on the first line: `§XX arguments`, `[[XX]] arguments`, `⟦XX⟧ arguments`.

| Code | Function | What it does | Arguments |
|---|---|---|---|
| `§SR` | SEARCH | look something up on the web | `<words>` |
| `§SH` | SHELL | run one shell command on this device (the pilot approves it first) | `<command>` |
| `§TL` | TOOL | use a connector tool | `<tool> <argument>` |
| `§CA` | CALC | work out arithmetic exactly | `<expression>` |
| `§PH` | PHOTO | show a real photograph | `<subject>` |
| `§BD` | BUILD | model a thing in 3D | `<thing, with every size given>` |
| `§SO` | SOCIAL | the pilot's social accounts | `SEARCH <network>: <words> \| TRENDING <network> \| CONNECT <network> \| POST <network>: <about, and any people to tag by name> \| COMMENT <network>: <post link> <what to say>` |
| `§SI` | SOCIALINTEL | social intelligence: investigate posts, find similar posts, public sentiment, profiles | `<capability> <arguments>` |
| `§SP` | SCHEDULEPOST | schedule a post on the pilot's own account, once or repeating; the pilot sees it and says yes first. LIST shows the scheduled posts | `<network> <yyyy-MM-dd HH:mm \| HH:mm \| in 2h> [daily\|weekly\|weekdays\|every 6h]: <text> \| LIST` |
| `§MF` | MAKEFILE | make a file of the last answer | `<pdf\|docx\|xlsx\|csv\|md\|html\|txt>` |
| `§AC` | ACTION | share, post or copy text through the device's apps (shown first; the pilot says yes) | `<post on <network>: text \| share: text \| copy: text>` |
| `§TM` | TIME | say the time and date | none |
| `§NM` | NAME | remember what to call the pilot | `<name>` |
| `§UZ` | UNZIP | unzip the zip file just shared | none |
| `§FZ` | FINDZIP | find zip files on this device | none |
| `§NC` | NEWCHAT | start a new chat | none |
| `§SW` | SWITCH | switch to another persona | `<ivie\|obi\|ibiko>` |
| `§HO` | HANDOVER | pass this message to the one whose work it is, who then answers it: ibiko for code, commands, files, repositories, GitHub and the built-in Linux; ivie for stories, scripts, poems, drawings, design and 3D; obi for conversation, questions, research, reports and plans. Only when it is not your own work | `<ivie\|obi\|ibiko>` |
| `§WP` | WRITEPLAN | a long piece (a story, a script, a report, an essay): outline it, check the outline, then write it section by section | none |
| `§TT` | THINKTOGETHER | a weighty decision or question: Obi, Ivie and Ibiko work on it together and give one answer | none |
| `§RF` | READFILES | read the files in the workspace on this device (cloned repositories, projects) to answer this | none |
| `§CO` | CARRYON | carry on with the unfinished work in this chat from where it stopped | none |
| `§DR` | CANVAS | change what is on Ivie's canvas: edit a part, turn or view it, undo, save, open or export | `<the change, in plain words>` |
| `§YS` | CONFIRM | the pilot agrees to the action that is shown waiting for their yes: carry it out | none |
| `§CX` | CANCEL | the pilot does not want the action that is shown waiting: drop it | none |
| `§PM` | PLANMODE | Ibiko plans and asks before changing anything | none |
| `§AO` | AUTOON | Ibiko runs commands, pushes and pull requests without asking | none |
| `§AF` | AUTOOFF | Ibiko asks before commands, pushes and pull requests | none |
| `§RT` | RETRY | ask the last question again | none |
| `§OS` | SETTINGS | open Settings | none |

Examples: `§SR weather in Lagos` · `§SW ibiko` · `§MF pdf` · `§SP x 9am daily: Good morning` · `§AC post on x: We're live`.

Rules: one code a reply; arithmetic goes to `§CA`; never claim a function ran (DVINE runs it after the code and shows the result); shell commands, posts and shares wait for the user's yes.
