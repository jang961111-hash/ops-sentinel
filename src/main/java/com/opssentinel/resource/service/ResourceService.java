package com.opssentinel.resource.service;

import com.opssentinel.resource.entity.Resource;
import com.opssentinel.resource.entity.ResourceType;
import com.opssentinel.resource.repository.ResourceRepository;
import com.opssentinel.resource.web.dto.CreateResourceRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 리소스 등록·조회 서비스.
 */
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;

    public Resource create(CreateResourceRequest request) {
        Resource resource = Resource.builder()
                .name(request.name())
                .type(request.type())
                .build();
        return resourceRepository.save(resource);
    }

    public Page<Resource> list(ResourceType type, Pageable pageable) {
        return resourceRepository.search(type, pageable);
    }

    public Resource getById(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + id));
    }
}
