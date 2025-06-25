<?php
include_once "dbe_gbl_Common.inc";
include_once "./common/dbe_gbl_tools.php";
include_once "common/dbe_obj_DatabaseAccess.php";

$link_id = DbConnect();
if (!$link_id) {
    error_message("Connection could not be established");
}

// Access cookies
$userid = isset($_COOKIE["userid"]) ? $_COOKIE["userid"] : '';

// Declare default values
$codusr = '';
$todo_type = '';
$codgrp = '';
$emailids = '';
$firstvisit = '';
$rptCode = '';

// Use POST if available, else fall back to GET
if (isset($_POST['firstVisit'])) {
    $firstvisit = $_POST['firstVisit'];
    $codusr = isset($_POST['codUsr']) ? $_POST['codUsr'] : '';
    $todo_type = isset($_POST['todo_type']) ? $_POST['todo_type'] : '';
    $codgrp = isset($_POST['codgrp']) ? $_POST['codgrp'] : '';
    $emailids = isset($_POST['txt_emailid']) ? $_POST['txt_emailid'] : '';
    $rptCode = isset($_POST['rptCode']) ? (int)$_POST['rptCode'] : 0;
} else {
    $codusr = isset($_GET['codUsr']) ? $_GET['codUsr'] : '';
    $todo_type = isset($_GET['todo_type']) ? $_GET['todo_type'] : '';
    $codgrp = isset($_GET['codgrp']) ? $_GET['codgrp'] : '';
    $rptCode = isset($_GET['rptCode']) ? (int)$_GET['rptCode'] : 0;
}

// Now $rptCode is safely available for the condition below
?>
