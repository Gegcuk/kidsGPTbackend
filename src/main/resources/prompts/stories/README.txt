STORY PROMPTS SYSTEM
=====================

This directory contains age-specific prompts for the "make story" endpoint functionality.
These prompts are designed to motivate and guide children in creating interesting, engaging stories.

FILE STRUCTURE:
- age-6-8.txt    -> Stories for early elementary kids (simple, magical, encouraging)
- age-9-10.txt   -> Stories for middle elementary kids (more complex plots, character development)
- age-11-12.txt  -> Stories for pre-teens (sophisticated themes, deeper character work)
- age-13-14.txt  -> Stories for early teens (complex themes, advanced techniques)
- age-15-16.txt  -> Stories for older teens (professional-level concepts, artistic voice)

HOW IT WORKS:
1. The StoryService determines the user's age group from their profile
2. Loads the appropriate prompt file for that age group
3. Uses the prompt as the system message to guide the AI's storytelling assistance
4. Combines this with encouraging templates that decorate the user's message
5. Creates an interactive storytelling experience tailored to the child's developmental level

DESIGN PRINCIPLES:
- Age-appropriate complexity and themes
- Encouraging and supportive tone
- Focus on creativity and imagination
- Safe, positive content
- Progressive skill-building as children grow

TEMPLATE INTEGRATION:
The service also uses motivational templates like:
- "Let's create an amazing story! [user message] What happens next in your story?"
- "Great story idea! [user message] Can you tell me more about the characters?"

This combination of age-specific prompts and encouraging templates creates a personalized
storytelling mentor experience for each child. 