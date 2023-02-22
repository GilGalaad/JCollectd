package jcollectd.common;

public class ReportUtils {

    private ReportUtils() {
    }

    public static final String HTML_BASE_INDENT = "    ";
    public static final String HTML_3X_INDENT = HTML_BASE_INDENT + HTML_BASE_INDENT + HTML_BASE_INDENT;
    public static final String HTML_4X_INDENT = HTML_BASE_INDENT + HTML_BASE_INDENT + HTML_BASE_INDENT + HTML_BASE_INDENT;

    public static final String templateHtml = """
            <!DOCTYPE html>
            <html lang="en">
                <head>
                    <title>XXX_TITLE_XXX</title>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <meta charset="UTF-8">
                    <link rel="icon" type="image/png" href="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAIGNIUk0AAHolAACAgwAA+f8AAIDpAAB1MAAA6mAAADqYAAAXb5JfxUYAAABkSURBVHjaYvz//z8DJYCpfv+rJIoM2Hzz81yKDMAmaDTj7n+yDYBpJtYQJnyS2AxBF2MiRQM2eSZi/G004+5/XAYxMVAIBs4AmDcodgELjHEuQ5mRbANISXnogJHi3EhpGAAGAD1lMjFNCeWlAAAAAElFTkSuQmCC" />
                    <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Roboto" />
                    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.11.2/css/all.css" integrity="sha256-46qynGAkLSFpVbEBog43gvNhfrOj+BmwXdxFgVK/Kvc=" crossorigin="anonymous" />
                    <style>
                        * {
                            box-sizing: border-box;
                        }
                        body {
                            background-color: rgb(42, 64, 81);
                            font-family: 'Roboto';
                        }
                        .navbar {
                            position: fixed;
                            top: 0;
                            left: 0;
                            right: 0;
                            z-index: 1;
                            background-color: rgba(8, 34, 53, 0.9);
                            border-bottom: 3px solid rgb(106, 129, 148);
                            padding: 0.5%;
                            text-align: center;
                            color: rgb(49, 151, 220);
                            text-shadow: 0 0 10px rgb(0, 0, 255), 0 0 10px rgb(0, 0, 255), 0 0 15px rgba(255, 255, 255, 0.9);
                            box-shadow: 0px 15px 15px -15px rgba(0, 0, 0, 0.8);
                        }
                        .navbar span {
                            display: inline-block;
                            width: 49%;
                        }
                        .navbar .navleft {
                            text-align: left;
                            font-size: 26px;
                        }
                        .navbar .navright {
                            text-align: right;
                            font-size: 16px;
                        }
                        .footer {
                            width: 100%;
                            padding-left: 3%;
                            padding-top: 0.4%;
                            padding-bottom: 0.8%;
                            font-size: 16px;
                            color: rgb(190, 190, 190);
                            clear: both;
                        }
                        .main-container {
                            width: 98%;
                            margin-left: auto;
                            margin-right: auto;
                            position: relative;
                            top: 60px;
                            text-shadow: 0 0 10px rgb(255, 255, 255);
                        }
                        .chart-container {
                            margin: 0.5%;
                            float: left;
                            background-color: rgba(8, 34, 53, 0.6);
                            border: 3px solid rgb(106, 129, 148);
                            border-radius: 2px;
                            box-shadow: 0px 15px 15px -15px rgba(0, 0, 0, 0.8);
                        }
                        .full-size {
                            width: 99%;
                        }
                        .half-size {
                            width: 49%;
                        }
                        @media only screen and (max-width: 1366px) {
                            .half-size {
                                width: 99%;
                            }
                        }
                    </style>
                    <script src="https://www.gstatic.com/charts/loader.js"></script>
                    <script>
                        google.charts.load('45', {'packages': ['corechart']});
                        <!-- start of generated JS data -->
                        XXX_JSDATA_XXX
                        <!-- end of generated JS data -->
                    </script>
                </head>
                <body>
                    <div class="navbar">
                        <span class="navleft"><i class="fas fa-chart-area" style="margin-right: 0.25em"></i> XXX_HOSTNAME_XXX</span>
                        <span class="navright"><i class="fas fa-clock" style="margin-right: 0.25em"></i> XXX_DATE_XXX</span>
                    </div>
                    <div class="main-container">
                        <!-- start of generated charts -->
                        XXX_BODY_XXX
                        <!-- end of generated charts -->
                        <div class="footer">
                            <p>XXX_TIMINGS_XXX</p>
                        </div>
                    </div>
                </body>
            </html>""";

    public static final String optsLoadJs = """
                            var date_formatter = new google.visualization.DateFormat({pattern: 'dd/MM/yyyy HH:mm:ss'});
                            date_formatter.format(data, 0);
                            var options = {
                                title: 'Average Load',
                                colors: ['#3366cc', '#ff9900', '#dc3912'],
                                backgroundColor: {fill: 'transparent'},
                                height: 350,
                                fontName: 'Roboto',
                                titleTextStyle: {color: '#eeeeee', fontSize: 16, bold: false},
                                legend: {
                                    textStyle: {color: '#eeeeee'},
                                    position: 'bottom'
                                },
                                hAxis: {
                                    textStyle: {color: '#eeeeee'},
                                    format: 'HH:mm'
                                },
                                vAxis: {
                                    textStyle: {color: '#eeeeee'},
                                    format: '#0.00',
                                    minValue: 0
                                },
                                tooltip: {
                                    textStyle: {color: '#162e40'}
                                },
                                animation: {
                                    duration: 500,
                                    easing: 'linear',
                                    startup: true
                                },
                                chartArea: {
                                    width: '100%',
                                    left: 75,
                                    right: 50
                                }
                            };
            """;

    public static final String optsCpuJs = """
                            var date_formatter = new google.visualization.DateFormat({pattern: 'dd/MM/yyyy HH:mm:ss'});
                            date_formatter.format(data, 0);
                            var options = {
                                title: 'CPU utilization percent',
                                colors: ['#3366cc'],
                                backgroundColor: {fill: 'transparent'},
                                height: 350,
                                fontName: 'Roboto',
                                titleTextStyle: {color: '#eeeeee', fontSize: 16, bold: false},
                                legend: {
                                    textStyle: {color: '#eeeeee'},
                                    position: 'bottom'
                                },
                                hAxis: {
                                    textStyle: {color: '#eeeeee'},
                                    format: 'HH:mm'
                                },
                                vAxis: {
                                    textStyle: {color: '#eeeeee'},
                                    format: '#\\'%\\'',
                                    minValue: 0,
                                    maxValue: 100
                                },
                                tooltip: {
                                    textStyle: {color: '#162e40'}
                                },
                                animation: {
                                    duration: 500,
                                    easing: 'linear',
                                    startup: true
                                },
                                chartArea: {
                                    width: '100%',
                                    left: 75,
                                    right: 50
                                }
                            };
            """;

    public static final String optsMemJs = """
                            var date_formatter = new google.visualization.DateFormat({pattern: 'dd/MM/yyyy HH:mm:ss'});
                            date_formatter.format(data, 0);
                            var options = {
                                title: 'Memory usage (in MiB)',
                                colors: ['#3366cc', '#dc3912', '#ff9900'],
                                backgroundColor: {fill: 'transparent'},
                                height: 350,
                                isStacked: true,
                                fontName: 'Roboto',
                                titleTextStyle: {color: '#eeeeee', fontSize: 16, bold: false},
                                legend: {
                                    textStyle: {color: '#eeeeee'},
                                    position: 'bottom'
                                },
                                hAxis: {
                                    textStyle: {color: '#eeeeee'},
                                    format: 'HH:mm'
                                },
                                vAxis: {
                                    textStyle: {color: '#eeeeee'},
                                    format: 'decimal',
                                    minValue: 0
                                },
                                tooltip: {
                                    textStyle: {color: '#162e40'}
                                },
                                animation: {
                                    duration: 500,
                                    easing: 'linear',
                                    startup: true
                                },
                                chartArea: {
                                    width: '100%',
                                    left: 75,
                                    right: 50
                                }
                            };
            """;

    public static final String optsNetJs = """
                            var date_formatter = new google.visualization.DateFormat({pattern: 'dd/MM/yyyy HH:mm:ss'});
                            date_formatter.format(data, 0);
                            var options = {
                                title: 'Network usage: REPLACEME (in KiB/s)',
                                colors: ['#109618', '#3366cc'],
                                backgroundColor: {fill: 'transparent'},
                                height: 350,
                                fontName: 'Roboto',
                                titleTextStyle: {color: '#eeeeee', fontSize: 16, bold: false},
                                legend: {
                                    textStyle: {color: '#eeeeee'},
                                    position: 'bottom'
                                },
                                hAxis: {
                                    textStyle: {color: '#eeeeee'},
                                    format: 'HH:mm'
                                },
                                vAxis: {
                                    textStyle: {color: '#eeeeee'},
                                    format: 'decimal'
                                },
                                tooltip: {
                                    textStyle: {color: '#162e40'}
                                },
                                animation: {
                                    duration: 500,
                                    easing: 'linear',
                                    startup: true
                                },
                                chartArea: {
                                    width: '100%',
                                    left: 75,
                                    right: 50
                                }
                            };
            """;

    public static final String optsDiskJs = """
                            var date_formatter = new google.visualization.DateFormat({pattern: 'dd/MM/yyyy HH:mm:ss'});
                            date_formatter.format(data, 0);
                            var options = {
                                title: 'Disk usage: REPLACEME (in MiB/s)',
                                colors: ['#109618', '#3366cc'],
                                backgroundColor: {fill: 'transparent'},
                                height: 350,
                                fontName: 'Roboto',
                                titleTextStyle: {color: '#eeeeee', fontSize: 16, bold: false},
                                legend: {
                                    textStyle: {color: '#eeeeee'},
                                    position: 'bottom'
                                },
                                hAxis: {
                                    textStyle: {color: '#eeeeee'},
                                    format: 'HH:mm'
                                },
                                vAxis: {
                                    textStyle: {color: '#eeeeee'},
                                    format: 'decimal'
                                },
                                tooltip: {
                                    textStyle: {color: '#162e40'}
                                },
                                animation: {
                                    duration: 500,
                                    easing: 'linear',
                                    startup: true
                                },
                                chartArea: {
                                    width: '100%',
                                    left: 75,
                                    right: 50
                                }
                            };
            """;

    public static final String optsGpuJs = """
                            var date_formatter = new google.visualization.DateFormat({pattern: 'dd/MM/yyyy HH:mm:ss'});
                            date_formatter.format(data, 0);
                            var options = {
                                title: 'GPU utilization percent',
                                colors: ['#3366cc'],
                                backgroundColor: {fill: 'transparent'},
                                height: 350,
                                fontName: 'Roboto',
                                titleTextStyle: {color: '#eeeeee', fontSize: 16, bold: false},
                                legend: {
                                    textStyle: {color: '#eeeeee'},
                                    position: 'bottom'
                                },
                                hAxis: {
                                    textStyle: {color: '#eeeeee'},
                                    format: 'HH:mm'
                                },
                                vAxis: {
                                    textStyle: {color: '#eeeeee'},
                                    format: '#\\'%\\'',
                                    minValue: 0,
                                    maxValue: 100
                                },
                                tooltip: {
                                    textStyle: {color: '#162e40'}
                                },
                                animation: {
                                    duration: 500,
                                    easing: 'linear',
                                    startup: true
                                },
                                chartArea: {
                                    width: '100%',
                                    left: 75,
                                    right: 50
                                }
                            };
            """;

}
