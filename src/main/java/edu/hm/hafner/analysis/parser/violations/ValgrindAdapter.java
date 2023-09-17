package edu.hm.hafner.analysis.parser.violations;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import edu.hm.hafner.analysis.IssueBuilder;
import edu.hm.hafner.analysis.Report;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import static j2html.TagCreator.*;

import se.bjurr.violations.lib.model.Violation;
import se.bjurr.violations.lib.parsers.ValgrindParser;

/**
 * Parses Valgrind XML report files.
 *
 * @author Tony Ciavarella
 */
public class ValgrindAdapter extends AbstractViolationAdapter {
    private static final long serialVersionUID = -6117336551972081612L;
    private static final int NUMBERED_STACK_THRESHOLD = 2;
    private static final int NO_LINE = -1;

    @Override
    ValgrindParser createParser() {
        return new ValgrindParser();
    }

    @Override
    Report convertToReport(final Set<Violation> violations) {
        try (IssueBuilder issueBuilder = new IssueBuilder()) {
            Report report = new Report();

            for (Violation violation: violations) {
                updateIssueBuilder(violation, issueBuilder);
                issueBuilder.setCategory("valgrind:" + violation.getReporter());
                issueBuilder.setDescription(generateDescriptionHtml(violation));
                report.add(issueBuilder.buildAndClean());
            }

            return report;
        }
    }

    private String generateDescriptionHtml(final Violation violation) {
        final Map<String, String> specifics = violation.getSpecifics();
        final JSONArray auxWhats = getAuxWhatsArray(specifics);

        return
                j2html.tags.DomContentJoiner.join(
                        "",
                        false,
                        generateGeneralTableHtml(violation.getSource(), violation.getGroup(), specifics.get("tid"), specifics.get("threadname"), auxWhats),
                        maybeGenerateStackTracesHtml(specifics.get("stacks"), violation.getMessage(), auxWhats),
                        maybeGenerateSuppressionHtml(specifics.get("suppression"))
                ).render();
    }

    private Tag generateGeneralTableHtml(final String executable, final String uniqueId, @CheckForNull final String threadId, @CheckForNull final String threadName, @CheckForNull final JSONArray auxWhats) {
        ContainerTag generalTable =
                table(
                        attrs(".table.table-striped"),
                        maybeGenerateTableRowHtml("Executable", executable),
                        maybeGenerateTableRowHtml("Unique Id", uniqueId),
                        maybeGenerateTableRowHtml("Thread Id", threadId),
                        maybeGenerateTableRowHtml("Thread Name", threadName)
                );

        if (auxWhats != null && !auxWhats.isEmpty()) {
            for (int auxwhatIndex = 0; auxwhatIndex < auxWhats.length(); ++auxwhatIndex) {
                generalTable.with(maybeGenerateTableRowHtml("Auxiliary", auxWhats.getString(auxwhatIndex)));
            }
        }

        return generalTable;
    }

    private Tag maybeGenerateStackTracesHtml(@CheckForNull final String stacksJson, final String message, @CheckForNull final JSONArray auxWhats) {
        if (StringUtils.isBlank(stacksJson)) {
            return iff(false, null);
        }

        final JSONArray stacks = new JSONArray(new JSONTokener(stacksJson));

        if (!stacks.isEmpty()) {
            ContainerTag stackTraces = div();

            stackTraces.with(generateStackTraceHtml("Primary Stack Trace", message, stacks.getJSONArray(0)));

            for (int stackIndex = 1; stackIndex < stacks.length(); ++stackIndex) {
                String msg = null;

                if (auxWhats != null && auxWhats.length() >= stackIndex) {
                    msg = auxWhats.getString(stackIndex - 1);
                }

                String title = "Auxiliary Stack Trace";

                if (stacks.length() > NUMBERED_STACK_THRESHOLD) {
                    title += " #" + stackIndex;
                }

                stackTraces.with(generateStackTraceHtml(title, msg, stacks.getJSONArray(stackIndex)));
            }

            return stackTraces;
        }

        return iff(false, null);
    }

    private Tag generateStackTraceHtml(final String title, @CheckForNull final String message, final JSONArray frames) {
        ContainerTag stackTraceContainer =
                div(
                        br(),
                        h4(title),
                        iff(StringUtils.isNotBlank(message), p(message))
                );

        for (int frameIndex = 0; frameIndex < frames.length(); ++frameIndex) {
            final JSONObject frame = frames.getJSONObject(frameIndex);

            if (frameIndex > 0) {
                stackTraceContainer.with(br());
            }

            stackTraceContainer.with(generateStackFrameHtml(frame));
        }

        return stackTraceContainer;
    }

    private Tag generateStackFrameHtml(final JSONObject frame) {
        return
                table(
                        attrs(".table.table-striped"),
                        maybeGenerateTableRowHtml("Object", frame.optString("obj")),
                        maybeGenerateTableRowHtml("Function", frame.optString("fn")),
                        maybeGenerateStackFrameFileTableRowHtml(frame)
                );
    }

    private Tag maybeGenerateSuppressionHtml(@CheckForNull final String suppression) {
        return
                iff(
                        StringUtils.isNotBlank(suppression),
                        div(br(), h4("Suppression"), table(attrs(".table.table-striped"), tr(td(pre(suppression)))))
                );
    }

    private Tag maybeGenerateTableRowHtml(final String name, @CheckForNull final String value) {
        return iff(StringUtils.isNotBlank(value), tr(td(text(name), td(text(value)))));
    }

    private Tag maybeGenerateStackFrameFileTableRowHtml(final JSONObject frame) throws JSONException {
        final String file = frame.optString("file");

        if (StringUtils.isNotBlank(file)) {
            final String dir = frame.optString("dir");
            final int line = frame.optInt("line", NO_LINE);
            final StringBuilder fileBuilder = new StringBuilder(256);

            if (StringUtils.isNotBlank(dir)) {
                fileBuilder.append(dir).append('/');
            }

            fileBuilder.append(file);

            if (line != NO_LINE) {
                fileBuilder.append(':').append(line);
            }

            return maybeGenerateTableRowHtml("File", fileBuilder.toString());
        }

        return iff(false, null);
    }

    @CheckForNull
    private JSONArray getAuxWhatsArray(final Map<String, String> specifics) {
        final String auxWhatsJson = specifics.get("auxwhats");
        return StringUtils.isNotBlank(auxWhatsJson) ? new JSONArray(new JSONTokener(auxWhatsJson)) : null;
    }
}