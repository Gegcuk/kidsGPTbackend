# Story API Documentation

## Overview

The Story API provides a comprehensive storytelling system for kids that manages the complete story lifecycle. Unlike the chat system, stories are structured conversations with dedicated entities and proper lifecycle management.

## 🎯 **Story Lifecycle**

```
START → IN_PROGRESS → COMPLETED/ARCHIVED
```

## 📋 **Endpoints**

### 1. Start Story
**POST** `/api/v1/stories/start`

Creates a new story with an encouraging initial prompt.

**Request:**
```json
{
  "title": "The Magic Adventure",
  "initialIdea": "A brave little hero starts an amazing journey" // Optional
}
```

**Response:**
```json
{
  "storyId": "uuid-here",
  "title": "The Magic Adventure", 
  "encouragingMessage": "What an exciting story! Let's create this magical adventure together...",
  "model": "gpt-4o-mini",
  "latencyMs": 150,
  "tokensUsed": 45,
  "createdAt": "2025-06-26T23:52:01.123"
}
```

### 2. Continue Story
**POST** `/api/v1/stories/{storyId}/continue`

Continues an existing story with new content.

**Request:**
```json
{
  "content": "The hero found a mysterious door in the forest."
}
```

**Response:**
```json
{
  "storyId": "uuid-here",
  "reply": "How exciting! What do you think is behind the mysterious door?",
  "model": "gpt-4o-mini", 
  "latencyMs": 120,
  "tokensUsed": 35
}
```

### 3. Get Story
**GET** `/api/v1/stories/{storyId}`

Retrieves complete story with all messages.

**Response:**
```json
{
  "id": "uuid-here",
  "title": "The Magic Adventure",
  "status": "IN_PROGRESS",
  "messages": [
    {
      "id": "uuid-here",
      "role": "USER", 
      "content": "I want to write a story called 'The Magic Adventure'...",
      "createdAt": "2025-06-26T23:52:01.123"
    },
    {
      "id": "uuid-here",
      "role": "ASSISTANT",
      "content": "What an exciting story! Let's create this magical adventure together...",
      "createdAt": "2025-06-26T23:52:01.456"
    }
  ],
  "createdAt": "2025-06-26T23:52:01.123",
  "updatedAt": "2025-06-26T23:52:01.456"
}
```

### 4. List Stories
**GET** `/api/v1/stories?page=0&size=20`

Lists user's stories with pagination.

**Response:**
```json
{
  "content": [
    {
      "id": "uuid-here",
      "title": "The Magic Adventure", 
      "status": "IN_PROGRESS",
      "messageCount": 4,
      "createdAt": "2025-06-26T23:52:01.123",
      "updatedAt": "2025-06-26T23:52:01.456"
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

## 🏗️ **Architecture**

### Entities
- **Story**: Main story entity with title, status, and messages
- **StoryMessage**: Individual messages in the story conversation
- **StoryStatus**: STARTED, IN_PROGRESS, COMPLETED, ARCHIVED

### Services
- **StoryService**: Core business logic
- **StoryServiceImpl**: Implementation with age-aware prompts

### DTOs
- **StartStoryRequest/Response**: For story creation
- **ContinueStoryRequest/Response**: For story continuation  
- **StoryDto**: Complete story details
- **StoryListDto**: Summary for listings
- **StoryMessageDto**: Individual message data

## 🎨 **Age-Specific Prompts**

The system uses different prompts based on user age:

- **Ages 6-8**: Simple, magical, encouraging
- **Ages 9-10**: More complex plots, character development
- **Ages 11-12**: Sophisticated themes, deeper character work
- **Ages 13-14**: Complex themes, advanced techniques  
- **Ages 15-16**: Professional-level concepts, artistic voice

## 🔒 **Security & Moderation**

- All user input validated with comprehensive moderation
- AI responses checked for age-appropriateness
- Users can only access their own stories
- JWT authentication required for all endpoints

## 🎯 **Key Features**

✅ **Dedicated Story Entities** - Separate from chat system
✅ **Proper Lifecycle Management** - Story status tracking
✅ **Age-Appropriate Prompts** - Tailored to development stage
✅ **Conversation History** - Full story context maintained
✅ **Pagination Support** - Efficient story listing
✅ **Safety & Moderation** - Comprehensive content validation
✅ **RESTful Design** - Clear, intuitive API structure

## 🚀 **Usage Examples**

### Creating a Story Flow
```bash
# 1. Start new story
POST /api/v1/stories/start
{
  "title": "Space Adventure",
  "initialIdea": "An astronaut discovers a new planet"
}

# 2. Continue the story
POST /api/v1/stories/{storyId}/continue  
{
  "content": "The planet had purple trees and singing rocks"
}

# 3. Get complete story
GET /api/v1/stories/{storyId}

# 4. List all stories
GET /api/v1/stories
```

This system provides a much more structured and manageable approach to storytelling compared to the original single endpoint design! 