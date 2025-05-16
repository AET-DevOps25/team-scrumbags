# Requirements Analysis

## Problem Statement
Modern development teams use a variety of platforms such as GitHub (code and issues), Discord (communication), and meeting notes to manage their projects. However, tracking team members' contributions, understanding project progress, and getting answers to questions like “Has issue X been resolved?” or “What has Person Y done this week?” requires manually combing through disparate sources, which is inefficient and error-prone.

The goal of this project is to create a system that can summarize the progress of a programming project. Therefore the software collects data from communication channels, repositories and meeting notes. The system will then use a GenAI service to summarize the data and provide concise overview of the projects status. 

## Main Functionality
Integration Connectors:
- connect to users repository GitHub (issues, commits, PRs)
- integrate communication services (primarily discord)
- Upload/import meeting notes either in video or text format
- provide time-framed summaries of activities per user or team
- answer questions about the projects status e.g. “Is the issue with the wrong colored buttons resolved?”

## Intended Users
- **Project Managers**:  want to track progress, blockers and team member contributions
- **Developers** want to acquire information about project status, requirement changes, collegue activities and past meeting content

## GenAI Integration
- Summarizing complex multi-source activity (e.g., combining GitHub commits + Discord chats + meeting notes)
- Semantic understanding of questions ("Is the button color issue fixed?") even if the exact terms don’t match the issue title
- Intelligent linking of distinct sources/actions (e.g., linking a commit to an issue and a related chat thread)

## Scenarios
### Scenario 1: Weekly Review
It’s Monday morning, and Alice, the project manager, logs into Scrumbags to check on the team’s progress from the past week. She selects a 7-day range, and the platform presents her with a concise activity summary.

She sees that she herself merged two pull requests and opened one new issue. Bob, one of the developers, resolved a bug related to a wrongly colored button and was actively discussing a new feature (dark mode) on Discord.

The system also provides a general project summary: five bugs have been resolved, two are still open, and the dark mode MVP was discussed in both Discord and the meeting notes.


### Scenario 2: Conversational Query
Bob, a developer, is unsure if the bug he has seen last on production has been resolved. Therefore he asks in the Scrumbags chat:
“Is the bug with not correctly aligned icons in mobile view still open?”

The system answers with:
“Issue #57 ‘Icons misaligned in mobile’ is still open. Last comment was from Jane on May 3rd mentioning pending UX feedback.”

### Scenario 3: Proactive Issue Tracking
During a team sync, the team discusses the new dark mode feature. Jane, the UX designer, is taking notes. After the meeting, she uploads the notes to Scrumbags. The system processes the notes, extracts key points and proposes new github issues based on the discussion:
Issue: Jane to test button alignment on Safari”

## Functional Requirements
### Integration
FR1.1: The user can connect his/her github account and specify a certain project which should be accessed

FR1.2: The user can integrate his/her communication channels (primarily Discord).

FR1.3: The user can upload meeting notes in textual and video form.

### Summary Engine
FR2.1: The system can generate summaries for specified date ranges.

FR2.2: Summary content can be limited to specified users.

FR2.3: The user can export the summary.

### Query Assistant
FR3.1: The user can ask questions in natural language

FR3.2: The user gets only project specific answers

FR3.3: The user can ask about project activities which are resolved to message, github or meeting references (actual entities)

### Proactive Issue Tracking
FR4.1: The system can proactively propose issues to the user based on the data it has collected (meeting notes, communication discussions).

# System Design

## Architecture

![Top Level Architecture Diagram](/docs/subsystem.drawio.png)

The TRACE system consists of 3 major parts:
1. **Fetching of Data** from external sources (GitHub, Discord, Meeting Notes)
2. **Processing of this Data** including storage of the retrieved data (mainly done by the GenAI service)
3. **Distribution of tasks and basic Project Management** e.g., storage of Project Metadata like begin date, members, etc.

The following describes each component in more detail:

**Client**: \
The client is an Angular web application that allows users to interact with the system. It provides a user-friendly interface for connecting to external services, uploading meeting notes, and querying the system.

**ProjectManagement**: \
The project management component is responsible for storing and managing project metadata, including team members, project start date, and other relevant information. This information is stored in a relational database. Furthermore the component acts like a central hub for the system, coordinating data flow between the client and the other services. 

**Transcription Service**: \
The transcription service is responsible for converting meeting notes (both video and text) into a format that can be processed by the GenAI service. This also includes extracting the speakers. The transcription text is then forwarded to the GenAI Service for storage. This service will most likely use a transcription model like Local Whisper.

**Communication Connector**: \
The communication connector is responsible for integrating with external communication platforms, primarily Discord. It fetches messages and relevant data from message channels periodically and forwards it to the GenAI service for storage and processing. The connector should allow to easily integrate other communication platforms in the future.

**GitHub Connector**: \
The GitHub connector is responsible for integrating with GitHub projects. It fetches issues, commits, and pull requests from the specified repositories and forwards this data to the GenAI service for storage and processing.

**GenAI Service**: \
The GenAI service processes the data collected from various sources, generates summaries, and provides answers to user queries. The service uses a large language model (LLM) to understand and process natural language queries and generate relevant responses. To do so all collected data is stored in a vectorized database.
