<?php
$SECRET = 'password';

$method = $_SERVER['REQUEST_METHOD'];
$function = $_REQUEST['f'];
$path = $_REQUEST['p'];
$secret = $_REQUEST['s'];

if ($secret != $SECRET) {
    http_response_code(401);
    return;
}

if ($function == 'di' and $method == 'GET') {
    if (!file_exists($path)) {
        http_response_code(404);
        return;
    }
    if (!is_dir($path)) {
        http_response_code(404);
        return;
    }
    header("Content-Type: application/json");
    $dirEntries = scandir($path);
    $count = 0;
    foreach ($dirEntries as $dirEntry) {
        if ($dirEntry == '.' or $dirEntry == '..')
            continue;
        $dirEntry = $path . "/" . $dirEntry;
        $dirEntry = str_replace("//", "/", $dirEntry);
        $filename = basename($dirEntry);
        $isDirectory = is_dir($dirEntry);
        $fileSize = filesize($dirEntry);
        $mtime = filemtime($dirEntry);
        $ctime = filectime($dirEntry);
        if ($count++ > 0)
            echo "\n";
        echo $filename . ";" . ($isDirectory ? 'true' : 'false') . ";" . $fileSize . ";" . $mtime . ";" . $mtime . ";" . $ctime;
    }
} else if ($function == 'fi' and $method == 'GET') {
    if (!file_exists($path)) {
        http_response_code(404);
        return;
    }
    $filename = basename($path);
    $isDirectory = is_dir($path);
    $fileSize = filesize($path);
    $atime = fileatime($path);
    $mtime = filemtime($path);
    $ctime = filectime($path);
    header("Content-Type: text/plain; charset=UTF-8");
    echo $filename . ";" . ($isDirectory ? 'true' : 'false') . ";" . $fileSize . ";" . $mtime . ";" . $mtime . ";" . $ctime;
} else if ($function == 'nl' and $method == 'GET') {
    if (!file_exists($path)) {
        http_response_code(404);
        return;
    }
    if (!is_dir($path)) {
        http_response_code(404);
        return;
    }
    $dirEntries = scandir($path);
    $count = 0;
    foreach ($dirEntries as $dirEntry) {
        if ($dirEntry == '.' or $dirEntry == '..')
            continue;
        $count++;
    }
    header("Content-Type: application/json");
    echo $count == 0 ? 'true' : 'false';
} else if ($function == 'md' and $method == 'POST') {
    if (file_exists($path)) {
        http_response_code(404);
        return;
    }
    mkdir($path);
} else if ($function == 'mf' and $method == 'POST') {
    if (file_exists($path)) {
        http_response_code(404);
        return;
    }
    file_put_contents($path, '');
} else if ($function == 'rf' and $method == 'POST') {
    if (!file_exists($path)) {
        http_response_code(404);
        return;
    }
    $newPath = $_REQUEST['n'];
    rename($path, $newPath);
} else if ($function == 'df' and $method == 'DELETE') {
    if (!file_exists($path)) {
        http_response_code(404);
        return;
    }
    if (is_dir($path)) {
        rmdir($path);
    } else {
        unlink($path);
    }
} else if ($function == 'rf' and $method == 'GET') {
    if (!file_exists($path)) {
        http_response_code(404);
        return;
    }
    if (is_dir($path)) {
        http_response_code(404);
        return;
    }
    if (!is_readable($path)) {
        http_response_code(404);
        return;
    }
    $offset = $_REQUEST['o'];
    $length = $_REQUEST['l'];
    header("Content-Type: application/octet-stream");
    if ($offset == filesize($path)) {
        http_response_code(201);
        return;
    }
    $fd = fopen($path, "r");
    fseek($fd, $offset);
    $data = fread($fd, $length);
    fclose($fd);
    echo $data;
} else if ($function == 'wf' and $method == 'PUT') {
    if (!file_exists($path)) {
        http_response_code(404);
        return;
    }
    if (is_dir($path)) {
        http_response_code(404);
        return;
    }
    if (!is_writable($path)) {
        http_response_code(404);
        return;
    }
    $offset = $_REQUEST['o'];
    $data = file_get_contents('php://input');
    $fd = fopen($path, "r+");
    fseek($fd, $offset);
    $result = fwrite($fd, $data);
    fclose($fd);
    header("Content-Type: text/plain");
    echo $result;
} else if ($function == 'sf' and $method == 'POST') {
    if (!file_exists($path)) {
        http_response_code(404);
        return;
    }
    if (is_dir($path)) {
        http_response_code(404);
        return;
    }
    if (!is_writable($path)) {
        http_response_code(404);
        return;
    }
    $length = $_REQUEST['l'];
    $fd = fopen($path, "r+");
    ftruncate($fd, $length);
    fclose($fd);
}