package com.resumescreening.service;

import com.resumescreening.model.Candidate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.Loader;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Service
public class ResumeService {

    // ── State (in-memory, persists until JD is cleared) ───────────────────────

    private String currentRole = "";
    private String currentJD   = "";
    private List<String> jdKeywords    = new ArrayList<>();
    private List<Candidate> candidates = new ArrayList<>();

    // ── Set / Clear JD ────────────────────────────────────────────────────────

    public Map<String, Object> setJD(String role, String jd) {
        this.currentRole = role.trim();
        this.currentJD   = jd.trim();
        this.candidates.clear();
        this.jdKeywords  = extractKeywords(jd);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message",      "JD set successfully for role: " + role);
        response.put("keywordCount", jdKeywords.size());
        response.put("keywords",     jdKeywords);
        return response;
    }

    public Map<String, Object> clearJD() {
        this.currentRole = "";
        this.currentJD   = "";
        this.jdKeywords.clear();
        this.candidates.clear();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "JD cleared. All candidate data has been removed.");
        return response;
    }

    // ── Process Resume ────────────────────────────────────────────────────────

    public Candidate processResume(MultipartFile file) throws IOException {
        String resumeText = extractTextFromPDF(file);
        String resumeLower = resumeText.toLowerCase();

        // --- Extract contact info ---
        String name  = extractName(resumeText, file.getOriginalFilename());
        String email = extractEmail(resumeText);
        String phone = extractPhone(resumeText);

        // --- Score: matched keywords / total keywords × 100 ---
        int matched = 0;
        for (String keyword : jdKeywords) {
            // Support multi-word keywords with word-boundary awareness
            String pattern = "(?i)\\b" + Pattern.quote(keyword) + "\\b";
            if (Pattern.compile(pattern).matcher(resumeLower).find()) {
                matched++;
            }
        }
        int total = jdKeywords.size();
        double score = total > 0
                ? Math.round(((double) matched / total) * 100.0 * 10) / 10.0
                : 0.0;

        Candidate candidate = new Candidate(
                name, email, phone, score, matched, total,
                file.getOriginalFilename()
        );

        // Add and re-sort descending by score
        candidates.removeIf(c -> c.getFilename().equalsIgnoreCase(file.getOriginalFilename()));
        candidates.add(candidate);
        candidates.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        return candidate;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public List<Candidate> getCandidates() { return candidates; }
    public String getCurrentRole()         { return currentRole; }
    public List<String> getJdKeywords()    { return jdKeywords; }
    public boolean isJdSet()               { return !currentRole.isEmpty() && !jdKeywords.isEmpty(); }

    // ── Export CSV ────────────────────────────────────────────────────────────

    public String exportCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Rank,Name,Email,Phone,Score (%),Matched Keywords,Total Keywords,File\n");
        int rank = 1;
        for (Candidate c : candidates) {
            sb.append(rank++).append(",")
              .append(csvCell(c.getName())).append(",")
              .append(csvCell(c.getEmail())).append(",")
              .append(csvCell(c.getPhone())).append(",")
              .append(c.getScore()).append(",")
              .append(c.getMatchedKeywords()).append(",")
              .append(c.getTotalKeywords()).append(",")
              .append(csvCell(c.getFilename())).append("\n");
        }
        return sb.toString();
    }

    private String csvCell(String val) {
        if (val == null || val.isBlank()) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    // ── PDF Text Extraction ───────────────────────────────────────────────────

    private String extractTextFromPDF(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    // ── Name Extraction ───────────────────────────────────────────────────────
    // Strategy: scan the first 20 lines for a short line that looks like a
    // human name (2–4 capitalized words, no digits, no @, no URL).

    private static final Set<String> NAME_BLOCKLIST = new HashSet<>(Arrays.asList(
            "curriculum", "vitae", "resume", "cv", "profile", "summary",
            "objective", "contact", "information", "details", "personal",
            "career", "professional", "portfolio", "biodata", "bio"
    ));

    private String extractName(String text, String filename) {
        String[] lines = text.split("\\r?\\n");
        int scanned = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (++scanned > 20) break;

            // Skip lines with email / URL / excessive digits
            if (line.contains("@") || line.contains("http") || line.matches(".*\\d{5,}.*")) continue;
            // Skip lines that are too long or too short
            if (line.length() < 3 || line.length() > 60) continue;
            // Must contain only letters, spaces, dots, apostrophes, hyphens
            if (!line.matches("[a-zA-Z .''\\-]+")) continue;

            String lower = line.toLowerCase();
            boolean blocked = NAME_BLOCKLIST.stream().anyMatch(lower::contains);
            if (blocked) continue;

            // Must look like 2–5 words of mostly title-case or all-caps tokens
            String[] words = line.trim().split("\\s+");
            if (words.length < 2 || words.length > 5) continue;

            // Each word should start with uppercase (or be all-caps abbreviation)
            boolean looksLikeName = Arrays.stream(words)
                    .allMatch(w -> w.length() >= 1 && Character.isUpperCase(w.charAt(0)));
            if (looksLikeName) return toTitleCase(line);
        }

        // Fallback: derive from filename
        if (filename != null && !filename.isBlank()) {
            String base = filename.replaceAll("(?i)\\.pdf$", "")
                                  .replaceAll("[_\\-]", " ")
                                  .replaceAll("[^a-zA-Z ]", "").trim();
            if (!base.isBlank()) return toTitleCase(base);
        }
        return "Unknown Candidate";
    }

    // ── Email Extraction ──────────────────────────────────────────────────────

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE
    );

    private String extractEmail(String text) {
        Matcher m = EMAIL_PATTERN.matcher(text);
        return m.find() ? m.group().toLowerCase() : "Not found";
    }

    // ── Phone Extraction ──────────────────────────────────────────────────────
    // Matches: +91-XXXXXXXXXX, (XXX) XXX-XXXX, XXXXXXXXXX (10 digits), etc.

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:(?:\\+?\\d{1,3}[\\s\\-.])?(?:\\(?\\d{3}\\)?[\\s\\-.])\\d{3}[\\s\\-.]\\d{4}" + // (XXX) XXX-XXXX
            "|(?:\\+?\\d{1,3}[\\s\\-])?\\d{10}" +                                               // +91 9876543210
            "|\\d{5}[\\s\\-]\\d{5})"                                                             // 98765 43210
    );

    private String extractPhone(String text) {
        Matcher m = PHONE_PATTERN.matcher(text);
        while (m.find()) {
            String raw = m.group().replaceAll("[^\\d+]", "");
            // Filter out year-like false positives (4 digit numbers)
            if (raw.replaceAll("\\D", "").length() >= 10) {
                return m.group().trim();
            }
        }
        return "Not found";
    }

    // ── Keyword Extraction ────────────────────────────────────────────────────
    // Preserves multi-word tech phrases (e.g. "Spring Boot", "REST APIs",
    // "machine learning") before falling back to single-word extraction.

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "and", "or", "the", "a", "an", "in", "on", "at", "to", "for",
            "of", "with", "is", "are", "be", "by", "as", "from", "that",
            "this", "will", "we", "our", "your", "have", "has", "it", "its",
            "not", "but", "if", "so", "do", "can", "may", "use", "used",
            "using", "such", "more", "including", "experience", "knowledge",
            "ability", "skills", "skill", "team", "work", "working", "role",
            "position", "candidates", "candidate", "required", "requirements",
            "must", "good", "strong", "proficient", "familiar", "understanding",
            "least", "years", "year", "minimum", "preferred", "plus", "should"
    ));

    // Well-known multi-word tech phrases to detect before splitting
    private static final List<String> MULTI_WORD_PHRASES = Arrays.asList(
            "spring boot", "spring mvc", "rest api", "rest apis", "restful api",
            "machine learning", "deep learning", "natural language processing",
            "data science", "data structures", "design patterns",
            "continuous integration", "continuous delivery", "ci/cd",
            "aws lambda", "google cloud", "microsoft azure",
            "node.js", "react.js", "vue.js", "next.js", "express.js",
            "object oriented", "object-oriented", "oop",
            "micro services", "microservices", "micro-services",
            "test driven", "test-driven", "tdd",
            "agile methodology", "scrum methodology",
            "version control", "source control",
            "apache kafka", "apache spark", "apache maven",
            "junit testing", "unit testing", "integration testing",
            "linux/unix", "linux unix"
    );

    private List<String> extractKeywords(String jd) {
        String jdLower = jd.toLowerCase();
        List<String> keywords = new ArrayList<>();

        // 1. Capture known multi-word phrases first
        for (String phrase : MULTI_WORD_PHRASES) {
            if (jdLower.contains(phrase)) {
                keywords.add(phrase);
            }
        }

        // 2. Split on delimiters and collect meaningful single/compound tokens
        String[] tokens = jd.split("[,;\\n\\r|•\\-–/()\\[\\]{}*]+");
        for (String token : tokens) {
            String t = token.trim().toLowerCase();
            // Accept tokens up to 4 words (captures "Spring Boot MVC" etc.)
            String[] parts = t.split("\\s+");
            if (parts.length >= 1 && parts.length <= 4) {
                // Single word: filter stop words and short tokens
                if (parts.length == 1) {
                    if (t.length() > 2 && !STOP_WORDS.contains(t) && t.matches("[a-z0-9#.+_/@]+")) {
                        keywords.add(t);
                    }
                } else {
                    // Multi-word: skip if all parts are stop words
                    boolean allStop = Arrays.stream(parts).allMatch(STOP_WORDS::contains);
                    if (!allStop && t.length() > 4) {
                        keywords.add(t);
                    }
                }
            }
        }

        // 3. Deduplicate (remove single-word tokens already covered by a phrase)
        List<String> deduped = new ArrayList<>();
        for (String kw : keywords.stream().distinct().collect(Collectors.toList())) {
            boolean coveredByPhrase = deduped.stream()
                    .anyMatch(existing -> existing.length() > kw.length() && existing.contains(kw));
            if (!coveredByPhrase) deduped.add(kw);
        }

        return deduped;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private String toTitleCase(String input) {
        if (input == null || input.isBlank()) return input;
        return Arrays.stream(input.trim().split("\\s+"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) +
                          (w.length() > 1 ? w.substring(1).toLowerCase() : ""))
                .collect(Collectors.joining(" "));
    }
}
