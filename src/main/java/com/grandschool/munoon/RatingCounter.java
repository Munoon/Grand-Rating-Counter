package com.grandschool.munoon;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class RatingCounter {
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat(".##");
    public static final String SESSION_COOKIE = "PHPSESSID";
    public static final String LOGIN_URL = "https://grandschool.com.ua/teachers/?login=yes";
    public static final String RATING_URL = "https://grandschool.com.ua/teachers/rating.php";

    public static void main(String[] args) throws IOException {
        FileInputStream input = new FileInputStream("./config.properties");
        Properties properties = new Properties();
        properties.load(input);

        String phpSession = getPhpSession(properties);
        List<Clazz> classes = getClasses(phpSession);
        addMarks(classes, phpSession, properties);

        classes.stream()
                .filter(item -> item.getAverage() != null)
                .sorted(Comparator.comparingDouble(Clazz::getAverage).reversed())
                .forEach(clazz -> System.out.println(String.format("%s (%s) - %s", clazz.getName(), clazz.getId(), clazz.getAverage())));
    }

    private static String getPhpSession(Properties properties) throws IOException {
        Connection.Response execute = Jsoup.connect(LOGIN_URL)
                .data(
                        "AUTH_FORM", "Y",
                        "TYPE", "AUTH",
                        "backurl", "/teachers/",
                        "USER_LOGIN", properties.getProperty("login"),
                        "USER_PASSWORD", properties.getProperty("password")
                )
                .method(Connection.Method.POST)
                .execute();

        return execute.cookie(SESSION_COOKIE);
    }

    private static List<Clazz> getClasses(String phpSession) throws IOException {
        Document data = Jsoup.connect(RATING_URL)
                .cookie(SESSION_COOKIE, phpSession)
                .post();

        Elements select = data.getElementsByTag("select");

        return select.first().children().stream()
                .filter(item -> !item.attributes().hasKey("disabled"))
                .map(item -> new Clazz(item.text(), item.attr("value")))
                .collect(Collectors.toList());
    }

    private static void addMarks(List<Clazz> classes, String phpSession, Properties properties) throws IOException {
        for (Clazz clazz : classes) {
            Document data = Jsoup.connect(RATING_URL)
                    .cookie(SESSION_COOKIE, phpSession)
                    .data(
                            "yam[class]", clazz.getId(),
                            "yam[date][from]", properties.getProperty("from_data"),
                            "yam[date][to]", properties.getProperty("to_data")
                    )
                    .post();

            Element tbody = data.getElementsByTag("tbody").get(2);

            List<Element> studentsList = tbody.children().stream()
                    .filter(item -> !tbody.selectFirst("tr:first-child").equals(item))
                    .collect(Collectors.toList());

            List<Double> studentsAverage = new ArrayList<>();
            studentsList.forEach(item -> {
                List<Integer> studentMarks = new ArrayList<>();

                Elements dashes = item.getElementsByTag("td");
                dashes.stream()
                        .filter(dash -> !dashes.get(0).equals(dash))
                        .filter(dash -> !dashes.get(1).equals(dash))
                        .filter(dash -> !dashes.get(2).equals(dash))
                        .filter(dash -> !dash.text().trim().equals("0"))
                        .forEach(dash -> studentMarks.add(Integer.parseInt(dash.text())));

                double sum = 0;
                for (Integer mark : studentMarks) {
                    sum += mark;
                }

                if (studentMarks.size() != 0) {
                    studentsAverage.add(sum / studentMarks.size());
                }
            });

            double sum = 0;
            for (Double mark : studentsAverage) {
                sum += mark;
            }

            String formatted = DECIMAL_FORMAT.format(sum / studentsAverage.size()).replace(",", ".");
            if (!formatted.equals("�")) {
                try {
                    clazz.setAverage(Double.parseDouble(formatted));
                } catch (Exception e) {
                    // do nothing
                }
            }
        }
    }

    static class Clazz {
        private String name;

        private String id;

        private Double average;

        public Clazz(String name, String id) {
            this.name = name;
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public String getId() {
            return id;
        }

        public Double getAverage() {
            return average;
        }

        public void setAverage(Double average) {
            this.average = average;
        }
    }
}
