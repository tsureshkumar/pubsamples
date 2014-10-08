var gulp = require('gulp'),
    less = require('gulp-less'),
    refresh = require('gulp-livereload'),
  lrserver = require('tiny-lr')(),
    express = require('express'),
    dest = './samples',
 livereload = require('connect-livereload'),
    livereloadport = 35729,
	serverport = 8000;


	//We only configure the server here and start it only when running the watch task
	var server = express();
	//Add livereload middleware before static-middleware
	server.use(livereload());
	server.use(express.static(dest));

gulp.task('serve', function() {
  //Set up your static fileserver, which serves files in the build dir
  server.listen(serverport);
 
  //Set up your livereload server
  lrserver.listen(livereloadport);
});

gulp.task('watch',['serve'],  function() {
  var server = refresh();
  console.log("starting livereload...");
  gulp.watch(dest + '/**/*',function(file) {
	//console.log("Changed... " + file.path);
//      refresh(lrserver);
	var fileName =  require('path').relative(dest, file.path);
	console.log("Changed... " + fileName);
	lrserver.changed({ body: { files: [fileName] }});	

  });
});
