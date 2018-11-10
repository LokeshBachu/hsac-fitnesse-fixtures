package nl.hsac.fitnesse.junit.reportmerge;

import nl.hsac.fitnesse.junit.HsacFitNesseRunner;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static nl.hsac.fitnesse.junit.reportmerge.TestReportHtml.ERROR_STATUS;
import static nl.hsac.fitnesse.junit.reportmerge.TestReportHtml.FAIL_STATUS;
import static nl.hsac.fitnesse.junit.reportmerge.TestReportHtml.IGNORE_STATUS;
import static nl.hsac.fitnesse.junit.reportmerge.TestReportHtml.NO_TEST_STATUS;
import static nl.hsac.fitnesse.junit.reportmerge.TestReportHtml.PASS_STATUS;

/**
 * Creates a (single) overview page based on a pre-existing set of FitNesse result HTML pages.
 * This is useful to generate a combined result page when multiple suites were run separately (e.g. in parallel).
 */
public class HtmlReportIndexGenerator {
    private static final String TESTCOUNT_CHART_ID = "countPie";
    private static final String RUNTIME_CHART_ID = "runtimePie";
    private static final String STATUS_CHART_ID = "statusPie";
    private final NumberFormat nf = NumberFormat.getIntegerInstance();

    public static void main(String[] arguments) throws IOException {
        String path = HsacFitNesseRunner.FITNESSE_RESULTS_PATH;
        if (arguments != null && arguments.length > 0) {
            path = arguments[0];
        }
        System.out.println("Generating based on: " + path);
        String output = new HtmlReportIndexGenerator().createFrom(path);
        System.out.println("Generated overview: " + output);
    }

    public String createFrom(String path) throws IOException {
        File parentDir = new File(path);
        if (!parentDir.exists() || !parentDir.isDirectory()) {
            throw new IllegalArgumentException(parentDir.getAbsolutePath() + " is not an existing directory");
        }

        List<TestReportHtml> reportHtmls = findTestResultPages(parentDir);
        if (reportHtmls.isEmpty()) {
            throw new IllegalArgumentException("No results found below: " + parentDir.getAbsolutePath());
        }

        return createOverviewFile(parentDir, reportHtmls);
    }

    protected String createOverviewFile(File parentDir, List<TestReportHtml> htmls) throws IOException {
        File newIndex = new File(parentDir, "index.html");
        createOverview(newIndex, htmls);

        return newIndex.getAbsolutePath();
    }

    protected List<TestReportHtml> findTestResultPages(File parentDir) throws IOException {
        TestReportFactory reportFactory = getReportFactory(parentDir);

        List<TestReportHtml> reportHtmls = Files.find(parentDir.toPath(), 2,
                (p, name) -> p.getFileName().toString().endsWith(".html"))
                .map(p -> p.toFile())
                .filter(this::isNotIndexHtml)
                .sorted()
                .map(reportFactory::create)
                .collect(Collectors.toList());
        for (TestReportHtml html : reportHtmls) {
            String runName = html.getRunName();
            long time = html.isOverviewPage() ?
                    reportFactory.getTime(runName)
                    : reportFactory.getTime(runName, html.getTestName());
            html.setTime(time);
        }
        return reportHtmls;
    }

    protected TestReportFactory getReportFactory(File parentDir) {
        return new TestReportFactory(parentDir);
    }

    protected void createOverview(File index, List<TestReportHtml> htmls) throws IOException {
        try (PrintWriter pw = new PrintWriter(index, "utf-8")) {
            writeHeader(pw, htmls);
            writeBody(pw, htmls);
            writeFooter(pw, htmls);
        }
    }

    protected void writeHeader(PrintWriter pw, List<TestReportHtml> htmls) {
        TestReportHtml firstTestPage = htmls.get(0);
        String firstRunCssDir = firstTestPage.getDirectory();
        pw.write("<html><head><meta http-equiv='Content-Type' content='text/html;charset=UTF-8'/><link rel='stylesheet' type='text/css' href='");
        pw.write(firstRunCssDir);
        pw.write("/css/fitnesse.css'/>");
        writeExtraHeaderContent(pw, htmls);
        pw.write("</head><body>");
    }

    protected void writeExtraHeaderContent(PrintWriter pw, List<TestReportHtml> htmls) {
        pw.write("<script type='text/javascript' src='https://www.gstatic.com/charts/loader.js'></script>");
    }

    protected void writeFooter(PrintWriter pw, List<TestReportHtml> htmls) {
        pw.write("</body></html>");
    }

    protected void writeBody(PrintWriter pw, List<TestReportHtml> htmls) {
        writeOverviewSection(pw, htmls);
        writeTestResultsSection(pw, htmls);
    }

    protected void writeOverviewSection(PrintWriter pw, List<TestReportHtml> htmls) {
        writeOverviewGraph(pw, htmls);

        List<TestReportHtml> overviewPages = filterBy(htmls, TestReportHtml::isOverviewPage);
        writeSection(pw, "Overview Pages", overviewPages);
    }

    protected void writeOverviewGraph(PrintWriter pw, List<TestReportHtml> htmls) {
        pw.write("<div style='width:100%;'>");

        List<TestReportHtml> testHtmls = filterBy(htmls,
                x -> !x.isOverviewPage()
                        && !NO_TEST_STATUS.equals(x.getStatus()));
        pw.write("<table style='width:100%;text-align:center;'><tr>");
        writeGraphCell(pw, ERROR_STATUS, testHtmls);
        writeGraphCell(pw, FAIL_STATUS, testHtmls);
        writeGraphCell(pw, IGNORE_STATUS, testHtmls);
        writeGraphCell(pw, PASS_STATUS, testHtmls);
        pw.write("</tr></table>");

        writePieChartsElements(pw, htmls);
        pw.write("</div>");
    }

    protected void writeGraphCell(PrintWriter pw, String status, List<TestReportHtml> testHtmls) {
        int totalCount = testHtmls.size();
        int count = filterByStatus(testHtmls, status).size();
        if (count > 0) {
            int pct = (count * 100) / totalCount;
            String cell = String.format("<td class=\"%s\" style=\"width:%s%%;\">%s</td>", status, pct, count);
            pw.write(cell);
        }
    }

    protected void writePieChartsElements(PrintWriter pw, List<TestReportHtml> htmls) {
        pw.write("<div style='display:flex;flex-wrap:wrap;justify-content:center;'>");
        pw.write("<div id='");
        pw.write(STATUS_CHART_ID);
        pw.write("'></div>");
        pw.write("<div id='");
        pw.write(TESTCOUNT_CHART_ID);
        pw.write("'></div>");
        pw.write("<div id='");
        pw.write(RUNTIME_CHART_ID);
        pw.write("'></div>");
        writeChartGenerators(pw, htmls);
        pw.write("</div>");
    }

    protected void writeChartGenerators(PrintWriter pw, List<TestReportHtml> htmls) {
        pw.write("<script type='text/javascript'>" +
                "if(window.google){google.charts.load('current',{'packages':['corechart']});" +
                "google.charts.setOnLoadCallback(drawChart);" +
                "function drawChart() {");

        Map<String, Long> displayedStatus = getStatusMap(htmls);

        writePieChartGenerator(pw, "Status", STATUS_CHART_ID,
                ",slices:[{color:'#ffffaa'},{color:'#FF6666'},{color:'orange'},{color:'#28B463'},{color:'lightgray'}]",
                r -> r.getKey(), r -> r.getValue(), displayedStatus.entrySet());

        List<Map.Entry<String, Long>> counts = sortBy(
                filterBy(htmls,
                        r -> !r.isOverviewPage()).stream()
                        .collect(Collectors.groupingBy(TestReportHtml::getRunName, Collectors.counting())).entrySet(),
                r -> r.getKey());
        writePieChartGenerator(pw, "Count", TESTCOUNT_CHART_ID, "",
                r -> r.getKey(), r -> r.getValue(), counts);

        List<TestReportHtml> overviewPages = sortBy(
                filterBy(htmls, TestReportHtml::isOverviewPage),
                r -> r.getRunName());
        writePieChartGenerator(pw, "Time", RUNTIME_CHART_ID, "",
                r -> r.getRunName(), r -> r.getTime(), overviewPages);
        pw.write("}}</script>");
    }

    protected <T> void writePieChartGenerator(PrintWriter pw,
                                              String title,
                                              String chartElementId,
                                              String slicesProperty,
                                              Function<T, String> keyFunction,
                                              Function<T, Number> valueFunction,
                                              Iterable<T> htmls) {
        StringBuilder data = new StringBuilder("[['Group',''],");
        htmls.forEach(r -> {
            data.append("['");
            data.append(keyFunction.apply(r));
            data.append("',");
            data.append(valueFunction.apply(r));
            data.append("],");
        });
        data.append("]");
        String dataArray = data.toString();

        pw.write("new google.visualization.PieChart(document.getElementById('");
        pw.write(chartElementId);
        pw.write("')).draw(google.visualization.arrayToDataTable(");
        pw.write(dataArray);
        pw.write("),{title:'");
        pw.write(title);
        pw.write("',sliceVisibilityThreshold:0,pieSliceTextStyle:{color:'black'}");
        pw.write(slicesProperty);
        pw.write("});");
    }

    protected void writeTestResultsSection(PrintWriter pw, List<TestReportHtml> htmls) {
        List<TestReportHtml> testHtmls = filterBy(htmls, x -> !x.isOverviewPage());
        pw.write("<div id=\"TestResults\">");
        List<TestReportHtml> erroredTests = filterByStatus(testHtmls, ERROR_STATUS);
        writeSection(pw, "Errored Tests", erroredTests);
        List<TestReportHtml> failedTests = filterByStatus(testHtmls, FAIL_STATUS);
        writeSection(pw, "Failed Tests", failedTests);

        List<TestReportHtml> ignoredTests = filterByStatus(testHtmls, IGNORE_STATUS);
        writeSection(pw, "Ignored Tests", ignoredTests);
        List<TestReportHtml> passedTests = filterByStatus(testHtmls, PASS_STATUS);
        writeSection(pw, "Passed Tests", passedTests);
        List<TestReportHtml> noTests = filterByStatus(testHtmls, NO_TEST_STATUS);
        writeSection(pw, "Pages Without Assertions", noTests);
        pw.write("</div>");
    }

    protected void writeSection(PrintWriter pw, String header, List<TestReportHtml> htmls) {
        if (!htmls.isEmpty()) {
            pw.write("<div id=\"");
            pw.write(header.replaceAll("\\s", ""));
            pw.write("\">");
            pw.write("<h2>");
            pw.write(header);
            pw.write("</h2>");
            writeTestsTable(pw, htmls);
            pw.write("</div>");
        }
    }

    protected void writeTestsTable(PrintWriter pw, List<TestReportHtml> htmls) {
        pw.write("<table><tr><th>Run</th><th>Name</th><th>Runtime (in milliseconds)</th></tr>");
        // slowest times at top
        htmls.sort((o1, o2) -> Long.compare(o2.getTime(), o1.getTime()));
        for (TestReportHtml test : htmls) {
            writeTestRow(pw, test);
        }
        pw.write("</table>");
    }

    protected void writeTestRow(PrintWriter pw, TestReportHtml html) {
        String testPageName = html.getRelativePath();
        String status = html.getStatus();
        String run = html.getRunName();
        String testName = html.getTestName();
        long time = html.getTime();
        pw.write("<tr class=\"");
        pw.write(status);
        pw.write("\">");
        pw.write("<td>");
        pw.write(run);
        pw.write("</td>");
        pw.write("<td><a href=\"");
        pw.write(testPageName);
        pw.write("\">");
        pw.write(testName);
        pw.write("</a></td><td>");
        pw.write(time < 0 ? "unknown" : nf.format(time));
        pw.write("</td></tr>");
    }

    protected Map<String, Long> getStatusMap(List<TestReportHtml> htmls) {
        Map<String, Long> statuses = filterBy(htmls, r -> !r.isOverviewPage()).stream()
                .collect(Collectors.groupingBy(TestReportHtml::getStatus, Collectors.counting()));

        Map<String, Long> displayedStatus = new LinkedHashMap<>();
        addStatusEntry(ERROR_STATUS, statuses, displayedStatus);
        addStatusEntry(FAIL_STATUS, statuses, displayedStatus);
        addStatusEntry(IGNORE_STATUS, statuses, displayedStatus);
        addStatusEntry(PASS_STATUS, statuses, displayedStatus);
        addStatusEntry(NO_TEST_STATUS, statuses, displayedStatus);
        return displayedStatus;
    }

    protected void addStatusEntry(String status, Map<String, Long> statuses, Map<String, Long> displayedStatus) {
        displayedStatus.put(status, statuses.getOrDefault(status, 0L));
    }

    protected static List<TestReportHtml> filterByStatus(List<TestReportHtml> htmls, String desiredStatus) {
        return filterBy(htmls, x -> desiredStatus.equals(x.getStatus()));
    }

    protected static <T> List<T> sortBy(Collection<T> values, Function<T, ? extends Comparable> function) {
        return values.stream().sorted((o1, o2) -> function.apply(o1).compareTo(function.apply(o2)))
                .collect(Collectors.toList());
    }

    protected static <T> List<T> filterBy(List<T> list, Predicate<T> predicate) {
        return list.stream().filter(predicate).collect(Collectors.toList());
    }

    protected boolean isNotIndexHtml(File file) {
        return !"index.html".equals(file.getName());
    }
}
