var date_formatter = new google.visualization.DateFormat({pattern: 'dd/MM/yyyy HH:mm:ss'});
date_formatter.format(data, 0);
var options = {
    title: 'Memory usage (in MiB)',
    colors: ['#3366cc', '#dc3912'],
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
        minValue: 0
    },
    tooltip: {
        textStyle: {color: '#082235'}
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