package io.github.dakshshukla.lookalike.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class LookalikeController {

    private final LookalikeService service;

    public LookalikeController(LookalikeService service) {
        this.service = service;
    }

    @PostMapping("/lookalikes")
    public LookalikeResponse lookalikes(@Valid @RequestBody LookalikeRequest request) {
        return service.lookalikes(request.toBrief());
    }
}
