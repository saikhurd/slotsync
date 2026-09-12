package com.slotsync.controller;

import com.slotsync.entity.Resource;
import com.slotsync.exception.ResourceNotFoundException;
import com.slotsync.repository.ResourceRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ResourceController {

    private final ResourceRepository resourceRepository;

    public ResourceController(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    public record CreateResourceRequest(@NotBlank String name, String description) {}

    @GetMapping("/resources")
    public List<Resource> listResources() {
        return resourceRepository.findAll();
    }

    @PostMapping("/admin/resources")
    public ResponseEntity<Resource> createResource(@RequestBody CreateResourceRequest request) {
        Resource resource = new Resource(request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(resourceRepository.save(resource));
    }

    @PutMapping("/admin/resources/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
        resource.setActive(false);
        resourceRepository.save(resource);
        return ResponseEntity.noContent().build();
    }
}
