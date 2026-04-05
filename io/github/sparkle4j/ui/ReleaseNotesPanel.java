package io.github.sparkle4j.ui;

import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Renders release notes from a URL in a JEditorPane.
 *
 * <ul>
 *   <li>HTML URLs: rendered as-is via JEditorPane's text/html support.</li>
 *   <li>Markdown URLs (.md or content-sniffed): converted to HTML.
 *       Uses flexmark-java if on the classpath; otherwise falls back to a minimal built-in converter.</li>
 *   <li>No URL: shows "No release notes available."</li>
 *   <li>Network failure: shows "Could not load release notes." — never blocks the dialog.</li>
 * </ul>
 */
final class ReleaseNotesPanel extends JEditorPane {

    private static final Logger log = Logger.getLogger(ReleaseNotesPanel.class.getName());
    private static final String NO_NOTES_HTML = "<html><body><p>No release notes available.</p></body></html>";
    private static final String LOAD_FAILED_HTML = "<html><body><p>Could not load release notes.</p></body></html>";

    private static final Pattern LINK_PATTERN = Pattern.compile("\\[(.+?)]\\((.+?)\\)");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern CODE_PATTERN = Pattern.compile("`(.+?)`");

    ReleaseNotesPanel(String releaseNotesUrl, String inlineDescription) {
        setEditable(false);
        setContentType("text/html");
        putClientProperty(HONOR_DISPLAY_PROPERTIES, true);
        loadContentAsync(releaseNotesUrl, inlineDescription);
    }

    private void loadContentAsync(String releaseNotesUrl, String inlineDescription) {
        if (releaseNotesUrl == null) {
            if (inlineDescription != null) {
                if (looksLikeMarkdown(inlineDescription)) {
                    setText(markdownToHtml(inlineDescription));
                } else if (inlineDescription.trim().startsWith("<")) {
                    setText(inlineDescription);
                } else {
                    setText("<html><body><p>" + htmlEscape(inlineDescription) + "</p></body></html>");
                }
            } else {
                setText(NO_NOTES_HTML);
            }
            return;
        }

        var thread = new Thread(() -> {
            String html;
            try {
                var content = new String(
                    URI.create(releaseNotesUrl).toURL().openStream().readAllBytes(),
                    StandardCharsets.UTF_8
                );
                if (releaseNotesUrl.toLowerCase().endsWith(".md") || looksLikeMarkdown(content)) {
                    html = markdownToHtml(content);
                } else {
                    html = content;
                }
            } catch (IOException e) {
                log.warning("Could not load release notes from " + releaseNotesUrl + ": " + e.getMessage());
                html = LOAD_FAILED_HTML;
            } catch (IllegalArgumentException e) {
                log.warning("Invalid release notes URL " + releaseNotesUrl + ": " + e.getMessage());
                html = LOAD_FAILED_HTML;
            }
            var finalHtml = html;
            SwingUtilities.invokeLater(() -> setText(finalHtml));
        });
        thread.setDaemon(true);
        thread.setName("sparkle4j-release-notes");
        thread.start();
    }

    private static boolean looksLikeMarkdown(String content) {
        var first = content.stripLeading();
        return first.startsWith("#") || first.startsWith("- ") || first.startsWith("* ");
    }

    private static String markdownToHtml(String markdown) {
        try {
            return flexmarkConvert(markdown);
        } catch (ClassNotFoundException e) {
            return minimalMarkdownToHtml(markdown);
        } catch (ReflectiveOperationException e) {
            return minimalMarkdownToHtml(markdown);
        }
    }

    /** Reflective call to flexmark-java so it remains an optional runtime dependency. */
    private static String flexmarkConvert(String markdown)
        throws ClassNotFoundException, ReflectiveOperationException {
        var optionsClass = Class.forName("com.vladsch.flexmark.util.data.MutableDataSet");
        var options = optionsClass.getDeclaredConstructor().newInstance();

        var parserClass = Class.forName("com.vladsch.flexmark.parser.Parser");
        var parserBuilder = parserClass.getMethod("builder", optionsClass).invoke(null, options);
        var parser = parserBuilder.getClass().getMethod("build").invoke(parserBuilder);
        var document = parser.getClass().getMethod("parse", String.class).invoke(parser, markdown);

        var rendererClass = Class.forName("com.vladsch.flexmark.html.HtmlRenderer");
        var rendererBuilder = rendererClass.getMethod("builder", optionsClass).invoke(null, options);
        var renderer = rendererBuilder.getClass().getMethod("build").invoke(rendererBuilder);
        var nodeClass = Class.forName("com.vladsch.flexmark.util.ast.Node");
        var html = (String) renderer.getClass().getMethod("render", nodeClass).invoke(renderer, document);

        return "<html><body style='font-family:sans-serif;padding:8px'>" + html + "</body></html>";
    }

    /** CommonMark subset: headings, bold, lists, code blocks, links, paragraphs. */
    private static String minimalMarkdownToHtml(String markdown) {
        var sb = new StringBuilder("<html><body style='font-family:sans-serif;padding:8px'>");
        var lines = markdown.split("\n", -1);
        boolean inCode = false;

        for (var line : lines) {
            if (line.startsWith("```")) {
                inCode = !inCode;
                sb.append(inCode ? "<pre><code>" : "</code></pre>");
            } else if (inCode) {
                sb.append(htmlEscape(line)).append('\n');
            } else if (line.startsWith("### ")) {
                sb.append("<h3>").append(inlineFormat(line.substring(4))).append("</h3>");
            } else if (line.startsWith("## ")) {
                sb.append("<h2>").append(inlineFormat(line.substring(3))).append("</h2>");
            } else if (line.startsWith("# ")) {
                sb.append("<h1>").append(inlineFormat(line.substring(2))).append("</h1>");
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                sb.append("<li>").append(inlineFormat(line.substring(2))).append("</li>");
            } else if (line.isBlank()) {
                sb.append("<br>");
            } else {
                sb.append("<p>").append(inlineFormat(line)).append("</p>");
            }
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String inlineFormat(String s) {
        var escaped = htmlEscape(s);
        escaped = LINK_PATTERN.matcher(escaped).replaceAll("<a href=\"$2\">$1</a>");
        escaped = BOLD_PATTERN.matcher(escaped).replaceAll("<b>$1</b>");
        escaped = CODE_PATTERN.matcher(escaped).replaceAll("<code>$1</code>");
        return escaped;
    }
}
