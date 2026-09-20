---
name: news
description: "Look up the latest news for the user from specified news sites. Provides authoritative URLs for politics, finance, society, world, tech, sports, and entertainment. Use web_fetch to open each URL and snapshot to get content, then summarize for the user."
metadata:
  builtin_skill_version: "1.2"
  emailclaw:
    emoji: "📰"
    requires: {}
---

# News Reference

When the user asks for "latest news", "what's in the news today", or "news in category X", use the **web_fetch** tool with the categories and URLs below: open the page, take a snapshot, then extract headlines and key points from the page content and reply to the user.

**Note**: If the primary website returns an error or fails to load, please attempt to visit the corresponding backup website.

## Categories and Sources

| Category | Primary Source (US) | Primary URL | Backup Source | Backup URL |
|---|---|---|---|---|
| **Politics** | AP News Politics | https://apnews.com/hub/politics | People's Daily · CPC News | https://cpc.people.com.cn/ |
| **Finance** | CNBC | https://www.cnbc.com/ | China Economic Net | http://www.ce.cn/ |
| **Society** | AP News US | https://apnews.com/hub/us-news | China News · Society | https://www.chinanews.com/society/ |
| **World** | AP News World | https://apnews.com/world-news | CGTN | https://www.cgtn.com/ |
| **Tech** | TechCrunch | https://techcrunch.com/ | Science and Technology Daily | https://www.stdaily.com/ |
| **Sports** | ESPN | https://www.espn.com/ | CCTV Sports | https://sports.cctv.com/ |
| **Entertainment** | Variety | https://variety.com/ | Sina Entertainment | https://ent.sina.com.cn/ |

## How to Use (web_fetch)

1. **Clarify the user's need**: Determine which category or categories (politics / finance / society / world / tech / sports / entertainment), or pick 1–2 to fetch.
2. **Pick the URL**: Use the Primary URL from the table for that category. **If the primary website fails to load or returns an error, fallback to the Backup URL.** For multiple categories, repeat the steps below for each URL.
3. **Open the page**: Call **web_fetch** with:
   ```json
   {"action": "open", "url": "https://apnews.com/hub/us-news"}
   ```
   Replace `url` with the corresponding URL from the table.
4. **Take a snapshot**: In the same session, call **web_fetch** again:
   ```json
   {"action": "snapshot"}
   ```
   Extract headlines, dates, and summaries from the returned page content.
5. **Summarize the reply**: Organize a short list (headline + one or two sentences + source) by time or importance; if a site is unreachable or times out, say so and suggest another source (or fallback to the backup source immediately).

## Notes

- Page structure may change when sites are updated; if extraction fails, say so and suggest the user open the link directly.
- When visiting multiple categories, run `open` for each URL, then `snapshot`, to avoid mixing content from different pages.
- You may include the original link in the reply so the user can open it.
