package com.n4monitor.dataassistant;

import com.n4monitor.common.ApiResponse;
import com.n4monitor.exception.BusinessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/data-assistant")
public class DataAssistantController {
    private final DataAssistantService assistant;
    private final Semaphore querySlots = new Semaphore(4);
    private final Set<String> queryingUsers = ConcurrentHashMap.newKeySet();

    public DataAssistantController(DataAssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAuthority('data-assistant:view')")
    public ApiResponse<DataAssistantService.Answer> ask(@RequestBody DataAssistantService.Question question,
                                                        Authentication authentication) {
        if (!querySlots.tryAcquire()) {
            throw new BusinessException(429, "Data Assistant is busy. Try again shortly.");
        }
        try {
            String username = authentication.getName();
            if (!queryingUsers.add(username)) {
                throw new BusinessException(429, "Wait for your current Data Assistant query to finish.");
            }
            try {
                var permissions = authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
                return ApiResponse.success(assistant.ask(question, permissions));
            } finally {
                queryingUsers.remove(username);
            }
        } finally {
            querySlots.release();
        }
    }
}
