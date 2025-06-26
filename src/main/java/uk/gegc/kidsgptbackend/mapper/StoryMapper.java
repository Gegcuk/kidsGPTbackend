package uk.gegc.kidsgptbackend.mapper;

import org.springframework.stereotype.Component;
import uk.gegc.kidsgptbackend.dto.story.StoryDto;
import uk.gegc.kidsgptbackend.dto.story.StoryListDto;
import uk.gegc.kidsgptbackend.dto.story.StoryMessageDto;
import uk.gegc.kidsgptbackend.model.story.Story;
import uk.gegc.kidsgptbackend.model.story.StoryMessage;

import java.util.List;

@Component
public class StoryMapper {

    public StoryDto toDto(Story story) {
        return new StoryDto(
                story.getId(),
                story.getTitle(),
                story.getStatus(),
                story.getMessages().stream()
                        .map(this::toMessageDto)
                        .toList(),
                story.getCreatedAt(),
                story.getUpdatedAt()
        );
    }

    public StoryListDto toListDto(Story story) {
        return new StoryListDto(
                story.getId(),
                story.getTitle(),
                story.getStatus(),
                story.getMessages().size(),
                story.getCreatedAt(),
                story.getUpdatedAt()
        );
    }

    public StoryMessageDto toMessageDto(StoryMessage message) {
        return new StoryMessageDto(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    public List<StoryDto> toDtoList(List<Story> stories) {
        return stories.stream()
                .map(this::toDto)
                .toList();
    }

    public List<StoryListDto> toListDtoList(List<Story> stories) {
        return stories.stream()
                .map(this::toListDto)
                .toList();
    }
} 