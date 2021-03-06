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