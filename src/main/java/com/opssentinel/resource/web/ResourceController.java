package com.opssentinel.resource.web;

import com.opssentinel.resource.entity.ResourceType;
import com.opssentinel.resource.service.ResourceService;
import com.opssentinel.resource.web.dto.CreateResourceRequest;
import com.opssentinel.resource.web.dto.ResourceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Resource", description = "감시 대상 리소스 등록/조회 API")
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @Operation(summary = "리소스 등록")
    @PostMapping
    public ResponseEntity<ResourceResponse> create(@Valid @RequestBody CreateResourceRequest request) {
        ResourceResponse response = ResourceResponse.from(resourceService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "리소스 목록 조회", description = "type으로 필터링 가능하며, 페이지 단위로 반환한다.")
    @GetMapping
    public Page<ResourceResponse> list(
            @Parameter(description = "리소스 타입 필터") @RequestParam(required = false) ResourceType type,
            Pageable pageable) {
        return resourceService.list(type, pageable).map(ResourceResponse::from);
    }

    @Operation(summary = "리소스 단건 조회")
    @GetMapping("/{id}")
    public ResourceResponse get(@PathVariable Long id) {
        return ResourceResponse.from(resourceService.getById(id));
    }
}
