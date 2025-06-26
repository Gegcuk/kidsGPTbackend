package uk.gegc.kidsgptbackend.service.story.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import uk.gegc.kidsgptbackend.dto.story.*;
import uk.gegc.kidsgptbackend.exception.ConversationFormatException;
import uk.gegc.kidsgptbackend.exception.RateLimitException;
import uk.gegc.kidsgptbackend.exception.ResourceNotFoundException;
import uk.gegc.kidsgptbackend.mapper.StoryMapper;
import uk.gegc.kidsgptbackend.model.story.Story;
import uk.gegc.kidsgptbackend.model.story.StoryMessage;
import uk.gegc.kidsgptbackend.model.story.StoryStatus;
import uk.gegc.kidsgptbackend.model.user.AgeGroup;
import uk.gegc.kidsgptbackend.model.user.User;
import uk.gegc.kidsgptbackend.repository.story.StoryRepository;
import uk.gegc.kidsgptbackend.repository.user.UserRepository;
import uk.gegc.kidsgptbackend.service.story.StoryService;
import uk.gegc.kidsgptbackend.util.ModerationUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class StoryServiceImpl implements StoryService {

    private final StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final ChatClient chatClient;
    private final ModerationUtil moderationUtil;
    private final StoryMapper storyMapper;

    @Value("classpath:prompts/stories/age-6-8.txt")
    private Resource storyPrompt6_8;
    
    @Value("classpath:prompts/stories/age-9-10.txt")
    private Resource storyPrompt9_10;
    
    @Value("classpath:prompts/stories/age-11-12.txt")
    private Resource storyPrompt11_12;
    
    @Value("classpath:prompts/stories/age-13-14.txt")
    private Resource storyPrompt13_14;
    
    @Value("classpath:prompts/stories/age-15-16.txt")
    private Resource storyPrompt15_16;

    @Value("classpath:prompts/stories/start-templates.txt")
    private Resource startTemplatesResource;
    
    @Value("classpath:prompts/stories/continue-templates.txt")
    private Resource continueTemplatesResource;

    // Fallback templates if file loading fails
    private static final String[] FALLBACK_START_TEMPLATES = {
            "What an exciting story title! Let's create something amazing together. %s",
            "I love that title! Let's bring this story to life. %s",
            "Great choice for a story! Let's start creating. %s",
            "Perfect title for an adventure! Let's begin. %s"
    };
    
    private static final String[] FALLBACK_CONTINUE_TEMPLATES = {
            "Fantastic storytelling! %s What happens next?",
            "I love where this is going! %s How should we continue?",
            "Amazing creativity! %s What exciting twist should we add?",
            "Your story is getting really interesting! %s What's the next part?"
    };

    private static final Logger logger = LoggerFactory.getLogger(StoryServiceImpl.class);
    private final Random random = new Random();

    @Override
    public StartStoryResponse startStory(StartStoryRequest request, Principal principal) {
        Instant start = Instant.now();
        
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Create new story
        Story story = new Story();
        story.setUsername(principal.getName());
        story.setTitle(request.title());
        story.setStatus(StoryStatus.STARTED);
        story = storyRepository.save(story);

        // Generate encouraging message with template
        String systemPrompt = loadStoryPrompt(user);
        String baseUserInput = request.initialIdea() != null && !request.initialIdea().trim().isEmpty()
                ? "I want to write a story called '" + request.title() + "'. " + request.initialIdea()
                : "I want to write a story called '" + request.title() + "'. Can you help me get started?";

        // Apply encouraging template
        String template = getRandomStartTemplate();
        String templatedUserInput = String.format(template, baseUserInput);

        String encouragingMessage = generateAiResponse(systemPrompt, templatedUserInput, user);
        
        // Save the initial exchange (save original user input, not templated)
        StoryMessage userMessage = new StoryMessage();
        userMessage.setRole("USER");
        userMessage.setContent(baseUserInput);
        story.addMessage(userMessage);

        StoryMessage assistantMessage = new StoryMessage();
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setContent(encouragingMessage);
        story.addMessage(assistantMessage);

        story.setStatus(StoryStatus.IN_PROGRESS);
        storyRepository.save(story);

        long latency = Duration.between(start, Instant.now()).toMillis();
        
        return new StartStoryResponse(
                story.getId(),
                story.getTitle(),
                encouragingMessage,
                "gpt-4o-mini",
                latency,
                0, // Token usage tracking can be added later
                story.getCreatedAt()
        );
    }

    @Override
    public ContinueStoryResponse continueStory(UUID storyId, ContinueStoryRequest request, Principal principal) {
        Instant start = Instant.now();
        
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Story story = storyRepository.findByIdAndUsername(storyId, principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Story not found"));

        // Validate user input
        if (!moderationUtil.validateComprehensive(request.content(), user, "story continuation")) {
            throw new IllegalArgumentException("User input flagged as unsafe for age group");
        }

        // Generate AI response with encouraging template
        String systemPrompt = loadStoryPrompt(user);
        List<Message> conversationHistory = buildConversationHistory(story);
        
        // Apply encouraging template to user's continuation
        String template = getRandomContinueTemplate();
        String templatedContent = String.format(template, request.content());
        conversationHistory.add(new UserMessage(templatedContent));
        
        String aiResponse = generateAiResponseWithHistory(systemPrompt, conversationHistory, user);

        // Save messages
        StoryMessage userMessage = new StoryMessage();
        userMessage.setRole("USER");
        userMessage.setContent(request.content());
        story.addMessage(userMessage);

        StoryMessage assistantMessage = new StoryMessage();
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setContent(aiResponse);
        story.addMessage(assistantMessage);

        storyRepository.save(story);

        long latency = Duration.between(start, Instant.now()).toMillis();
        
        return new ContinueStoryResponse(
                story.getId(),
                aiResponse,
                "gpt-4o-mini",
                latency,
                0
        );
    }

    @Override
    public StoryDto getStory(UUID storyId, Principal principal) {
        Story story = storyRepository.findByIdAndUsername(storyId, principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Story not found"));
        
        return storyMapper.toDto(story);
    }

    @Override
    public Page<StoryListDto> getStoriesByUser(Principal principal, Pageable pageable) {
        Page<Story> stories = storyRepository.findByUsernameOrderByUpdatedAtDesc(principal.getName(), pageable);
        return stories.map(storyMapper::toListDto);
    }

    private String generateAiResponse(String systemPrompt, String userInput, User user) {
        try {
            ChatResponse response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userInput)
                    .call()
                    .chatResponse();
            
            String aiResponse = Optional.ofNullable(response)
                    .map(ChatResponse::getResult)
                    .map(Generation::getOutput)
                    .map(AbstractMessage::getText)
                    .orElse("");

            // Validate AI response
            if (!moderationUtil.validateSafetyForAge(aiResponse, user.getAge() != null ? 
                    AgeGroup.fromAge(user.getAge()) : AgeGroup.AGE_9_10)) {
                return "Let's try a different approach for your story! What other ideas do you have?";
            }

            return aiResponse;
        } catch (Exception e) {
            logger.error("Error generating AI response", e);
            return "I'm excited to help with your story! Can you tell me more about what you'd like to write?";
        }
    }

    private String generateAiResponseWithHistory(String systemPrompt, List<Message> conversationHistory, User user) {
        try {
            ChatResponse response = chatClient.prompt()
                    .system(systemPrompt)
                    .messages(conversationHistory)
                    .call()
                    .chatResponse();
            
            String aiResponse = Optional.ofNullable(response)
                    .map(ChatResponse::getResult)
                    .map(Generation::getOutput)
                    .map(AbstractMessage::getText)
                    .orElse("");

            // Validate AI response
            if (!moderationUtil.validateSafetyForAge(aiResponse, user.getAge() != null ? 
                    AgeGroup.fromAge(user.getAge()) : AgeGroup.AGE_9_10)) {
                return "Let's try a different direction for your story! What other ideas do you have?";
            }

            return aiResponse;
        } catch (Exception e) {
            logger.error("Error generating AI response with history", e);
            return "That's interesting! Can you tell me more about what happens next in your story?";
        }
    }

    private List<Message> buildConversationHistory(Story story) {
        List<Message> messages = new ArrayList<>();
        
        for (StoryMessage msg : story.getMessages()) {
            if ("USER".equalsIgnoreCase(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("ASSISTANT".equalsIgnoreCase(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        
        return messages;
    }

    private String loadStoryPrompt(User user) {
        try {
            Resource promptResource = getPromptResourceForAge(user.getAge());
            return StreamUtils.copyToString(promptResource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to load story prompt for age {}", user.getAge(), e);
            return getDefaultStoryPrompt(user.getAge());
        }
    }

    private Resource getPromptResourceForAge(Integer age) {
        if (age == null) return storyPrompt9_10;
        
        return switch (AgeGroup.fromAge(age)) {
            case AGE_6_8 -> storyPrompt6_8;
            case AGE_9_10 -> storyPrompt9_10;
            case AGE_11_12 -> storyPrompt11_12;
            case AGE_13_14 -> storyPrompt13_14;
            case AGE_15_16 -> storyPrompt15_16;
        };
    }

    private String getDefaultStoryPrompt(Integer age) {
        String ageBasedPrompt = "You are talking to a " + (age != null ? age : 9) + "-year-old child. ";
        return ageBasedPrompt + "Help them create amazing stories! Be encouraging, creative, and ask questions that spark their imagination. Keep responses friendly and age-appropriate.";
    }

    private String[] loadStartTemplates() {
        try {
            String templatesContent = StreamUtils.copyToString(startTemplatesResource.getInputStream(), StandardCharsets.UTF_8);
            return templatesContent.trim().split("\n");
        } catch (IOException e) {
            logger.warn("Failed to load start templates from file, using fallback templates", e);
            return FALLBACK_START_TEMPLATES;
        }
    }

    private String[] loadContinueTemplates() {
        try {
            String templatesContent = StreamUtils.copyToString(continueTemplatesResource.getInputStream(), StandardCharsets.UTF_8);
            return templatesContent.trim().split("\n");
        } catch (IOException e) {
            logger.warn("Failed to load continue templates from file, using fallback templates", e);
            return FALLBACK_CONTINUE_TEMPLATES;
        }
    }

    private String getRandomStartTemplate() {
        String[] templates = loadStartTemplates();
        return templates[random.nextInt(templates.length)];
    }

    private String getRandomContinueTemplate() {
        String[] templates = loadContinueTemplates();
        return templates[random.nextInt(templates.length)];
    }
} 