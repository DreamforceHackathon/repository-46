# repository-46: voiceforce

Using Salesforce from wearables

## Get started
```bash
lein cljsbuild auto
node dist/voiceforce.js
curl localhost:1337/do-it
```

# demo

I’m driving to <customer> [meeting]
-> contact-name->opportunity

App: Good luck! Here is the latest update about this opportunity: bla bla bla [hardcode link between account and opportunity If needed]
-> opportunity-info

User: Who will attend? [meeting_attendees_context]
-> meeting-attendes

App: Roberto Foo, CTO and Jessica Bar, CMO

User: Tell me more about Roberto [contact]
-> contact-name->info

App: [fetching from LinkedIn?] <short bio>

POST-MEETING

User: Great news on <customer>
-> contact-name->opportunity

App: That sounds great!

User: Update opportunity size to $200,000 [opportunity_size_context]
-> update-opportunity-amount

App: Done

User: And inform Jon [share]
-> contact-name->email

App: [to @Jon on Chatter: “great news on <c>. opportunity size upgraded to $200K”] OK

User: version 1: Remind me to send the quote by next Friday [task]
      version 2: Submit a pricing approval (etc.)
-> set-reminder

EXTRA

User: How much have we closed <this month> [let’s show off Duckling!]?
-> period->amount
App: version 1: Give the actual $ amount
        version 2: Give the actual $ amount in the Hint AND show the chart on the phone...
