package com.resumescreening.controller;

import com.resumescreening.model.Candidate;
import com.resumescreening.service.ResumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class ResumeController {

    @Autowired
    private ResumeService resumeService;

    // ── POST /setJD ───────────────────────────────────────────────────────────
    // Body: role (String), jd (String)
    // Sets the active Job Description and extracts keywords.
    // Clears any previously uploaded candidates.

    @PostMapping("/setJD")
    public ResponseEntity<Map<String, Object>> setJD(
            @RequestParam("role") String role,
            @RequestParam("jd")   String jd) {
        if (role == null || role.isBlank() || jd == null || jd.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Both 'role' and 'jd' parameters are required."));
        }
        try {
            Map<String, Object> result = resumeService.setJD(role, jd);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── POST /clearJD ─────────────────────────────────────────────────────────
    // Clears the active JD and all candidate data.

    @PostMapping("/clearJD")
    public ResponseEntity<Map<String, Object>> clearJD() {
        try {
            Map<String, Object> result = resumeService.clearJD();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── POST /uploadResume ────────────────────────────────────────────────────
    // Body: file (MultipartFile PDF)
    // Extracts name, email, phone from PDF and scores against active JD.
    // Returns the Candidate object with score and contact info.

    @PostMapping("/uploadResume")
    public ResponseEntity<?> uploadResume(@RequestParam("file") MultipartFile file) {
        if (!resumeService.isJdSet()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No active Job Description. Please set a JD first."));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided."));
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only PDF files are supported."));
        }
        try {
            Candidate candidate = resumeService.processResume(file);
            return ResponseEntity.ok(candidate);
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process PDF: " + e.getMessage()));
        }
    }

    // ── GET /candidates ───────────────────────────────────────────────────────
    // Returns all candidates sorted by score descending.

    @GetMapping("/candidates")
    public ResponseEntity<List<Candidate>> getCandidates() {
        return ResponseEntity.ok(resumeService.getCandidates());
    }

    // ── GET /currentRole ──────────────────────────────────────────────────────
    // Returns the currently active role and keyword count.

    @GetMapping("/currentRole")
    public ResponseEntity<Map<String, Object>> getCurrentRole() {
        return ResponseEntity.ok(Map.of(
                "role",         resumeService.getCurrentRole(),
                "isSet",        resumeService.isJdSet(),
                "keywordCount", resumeService.getJdKeywords().size(),
                "keywords",     resumeService.getJdKeywords()
        ));
    }

    // ── GET /exportCSV ────────────────────────────────────────────────────────
    // Returns ranked candidate list as a downloadable CSV file.

    @GetMapping("/exportCSV")
    public ResponseEntity<byte[]> exportCSV() {
        String csv = resumeService.exportCsv();
        byte[] bytes = csv.getBytes();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("candidate_rankings.csv").build()
        );
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
