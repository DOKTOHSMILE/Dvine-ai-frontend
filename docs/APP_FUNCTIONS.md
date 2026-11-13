# Ibiko app functions

Generated from `suit/FunctionCode.java` (`FunctionCode.document()`); a test keeps this file in step. Do not edit by hand.

Every model that drives Ibiko (an API model, the offline model, Ibiko 1) controls the app the same way: it writes one function code alone on the first line of its reply, then a short sentence for the user. Ibiko carries the function out and shows the result. The user's own words never run a function; a chat's tools (the tools button) decide which codes that chat may use.

Accepted forms on the first line: `§XX arguments`, `[[XX]] arguments`, `⟦XX⟧ arguments`.

| Code | Function | What it does | Arguments |
|---|---|---|---|
| `§SR` | SEARCH | look something up on the web | `<words>` |
| `§SH` | SHELL | run one shell command on this device (the pilot approves it first) | `<command>` |
| `§TL` | TOOL | use a connector tool | `<tool> <argument>` |
| `§CA` | CALC | work out arithmetic exactly | `<expression>` |
| `§PH` | PHOTO | show a real photograph | `<subject>` |
| `§IM` | IMAGE | make a picture: an icon, a logo, an illustration, artwork or a picture of anything (not a real photograph of something that exists: §PH finds those) | `<what it shows: subject, style, colours, layout>` |
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
| `§WK` | WORK | real work for the agents, not a reply: write, fix, build, run, test or audit code; work on files, a repository or GitHub; run commands in the built-in Linux; build or audit an app's or a site's interface. The agent organisation plans it, splits it and carries it out | none |
| `§WP` | WRITEPLAN | a long piece (a story, a script, a report, an essay): outline it, check the outline, then write it section by section | none |
| `§TT` | THINKTOGETHER | a weighty decision or question: it is planned, checked against the real world and tested for what could break, then answered once | none |
| `§RF` | READFILES | read the files in the workspace on this device (cloned repositories, projects) to answer this | none |
| `§CO` | CARRYON | carry on with the unfinished work in this chat from where it stopped | none |
| `§ST` | STEER | only while a job runs in the background: pass the user's instruction to that job | `<the instruction>` |
| `§YS` | CONFIRM | the pilot agrees to the action that is shown waiting for their yes: carry it out | none |
| `§CX` | CANCEL | the pilot does not want the action that is shown waiting: drop it | none |
| `§PM` | PLANMODE | Ibiko plans and asks before changing anything | none |
| `§AO` | AUTOON | Ibiko runs commands, pushes and pull requests without asking | none |
| `§AF` | AUTOOFF | Ibiko asks before commands, pushes and pull requests | none |
| `§RT` | RETRY | ask the last question again | none |
| `§OS` | SETTINGS | open Settings | none |

Examples: `§SR weather in Lagos` · `§SW ibiko` · `§MF pdf` · `§SP x 9am daily: Good morning` · `§AC post on x: We're live`.

Rules: one code a reply; arithmetic goes to `§CA`; never claim a function ran (Ibiko runs it after the code and shows the result); shell commands, posts and shares wait for the user's yes.

## How to call each function

Where you are: the Ibiko app on the user's phone. Linux is Alpine, one shell. This chat's files are /workspace. A clone lives in /workspace/repos/<name>. A private GitHub repository clones and pulls when Connectors, GitHub, is signed in (a classic token needs the repo scope; a fine-grained token needs Contents). Never put a token in a command, a URL, or git config. The app supplies it. One function a reply: its code alone on the first line, then one short sentence. Do not say the function already ran. A shell command, a post and a share wait for the user's yes.
- §SR <words> — when they want the web searched. Write §SR weather in Lagos. Ibiko searches; answer from the pages.
- §SH <command> — one Linux command they approve first. Write §SH cd /workspace && git clone 'https://github.com/owner/name'. A private repository uses the signed-in token; do not add it. Pull is §SH git -C /workspace/repos/name pull.
- §TL <tool> <argument> — a connector tool that is on for this chat. Write §TL github.repos or §TL github.issues owner/name or §TL github.file owner/name/path. Skip a tool that was not listed.
- §CA <expression> — exact arithmetic. Write §CA (12+7)*3. Do not work the sum out yourself.
- §PH <subject> — a real photograph of something that exists. Write §PH a red panda. A picture to invent is §IM.
- §IM <what it shows: subject, style, colours, layout> — a picture to make (icon, logo, illustration, artwork), not a real photo. Write §IM a blue call icon, flat.
- §SO SEARCH <network>: <words> | TRENDING <network> | CONNECT <network> | POST <network>: <about, and any people to tag by name> | COMMENT <network>: <post link> <what to say> — their social accounts. Write §SO SEARCH x: cats, §SO TRENDING x, §SO CONNECT reddit, §SO POST x: hello, or §SO COMMENT x: <link> nice. Networks: x, facebook, instagram, reddit.
- §SI <capability> <arguments> — social intelligence. Write §SI social.post.investigate <text>, §SI social.search.posts x cats, §SI social.search.similar <text>, §SI social.sentiment.analyze <topic>, §SI social.accounts.list, §SI social.profile.read_public x <user>, or §SI social.profile.read_own x.
- §SP <network> <yyyy-MM-dd HH:mm | HH:mm | in 2h> [daily|weekly|weekdays|every 6h]: <text> | LIST — schedule a post they confirm first. Write §SP x 9am daily: Good morning (time is yyyy-MM-dd HH:mm, HH:mm, 9am, or in 2h; repeat is daily, weekly, weekdays, or every 6h). §SP LIST shows what is scheduled.
- §MF <pdf|docx|xlsx|csv|md|html|txt> — a file of the last answer. Write §MF pdf (also docx, xlsx, csv, md, html, txt).
- §AC <post on <network>: text | share: text | copy: text> — share, post or copy through the phone, shown first. Write §AC post on x: We're live, §AC share: hello, or §AC copy: hello.
- §TM — the time and date on this phone. Write §TM.
- §NM <name> — what to call the user. Write §NM Valentine.
- §UZ — unzip the zip they just shared. Write §UZ.
- §FZ — find zip files on this phone. Write §FZ.
- §NC — start a new chat. Write §NC.
- §SW <ivie|obi|ibiko> — switch persona. Write §SW ibiko (also ivie or obi).
- §HO <ivie|obi|ibiko> — pass this message to the persona whose work it is, and only when it is not yours. Write §HO ibiko for code, commands, files, repositories and Linux; §HO ivie for stories, scripts, poems, drawings and design; §HO obi for questions, research and plans.
- §WK — real code work for the agents: write, fix, build, run, test or audit, or work a repository. Write §WK. The user's message is the task.
- §WP — a long piece (story, script, report, essay): outline, then write section by section. Write §WP.
- §TT — a weighty decision: plan it, check it, then answer once. Write §TT.
- §RF — read the files in this chat's workspace to answer. Write §RF.
- §CO — carry on with unfinished work in this chat. Write §CO.
- §ST <the instruction> — only while a job is already running: pass an instruction to that job. Write §ST focus on the api first.
- §YS — they agree to the action that is waiting. Write §YS.
- §CX — they do not want the action that is waiting. Write §CX.
- §PM — plan mode: ask before changing anything. Write §PM.
- §AO — auto mode: commands, pushes and pull requests without asking. Write §AO.
- §AF — auto mode off: ask before commands, pushes and pull requests. Write §AF.
- §RT — ask the last question again. Write §RT.
- §OS — open Settings. Write §OS.
