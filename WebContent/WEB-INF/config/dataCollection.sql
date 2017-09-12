-- phpMyAdmin SQL Dump
-- version 4.6.6deb4
-- https://www.phpmyadmin.net/
--
-- Host: localhost:3306
-- Generation Time: Jun 30, 2017 at 05:02 AM
-- Server version: 5.7.18-0ubuntu0.17.04.1
-- PHP Version: 7.0.18-0ubuntu0.17.04.1

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `dataCollection`
--
CREATE DATABASE IF NOT EXISTS `dataCollection` DEFAULT CHARACTER SET latin1 COLLATE latin1_swedish_ci;
USE `dataCollection`;

-- --------------------------------------------------------

--
-- Table structure for table `KeyboardInput`
--

DROP TABLE IF EXISTS `KeyboardInput`;
CREATE TABLE IF NOT EXISTS `KeyboardInput` (
  `username` varchar(50) NOT NULL,
  `user` varchar(20) NOT NULL,
  `pid` varchar(10) NOT NULL,
  `start` varchar(10) NOT NULL,
  `xid` varchar(10) NOT NULL,
  `timeChanged` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `button` varchar(10) NOT NULL,
  `type` varchar(10) NOT NULL,
  `inputTime` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `insertTimestamp` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`username`,`user`,`pid`,`start`,`xid`,`timeChanged`,`inputTime`,`type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `LastTransfer`
--

DROP TABLE IF EXISTS `LastTransfer`;
CREATE TABLE IF NOT EXISTS `LastTransfer` (
  `lastTransfer` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`lastTransfer`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Dumping data for table `LastTransfer`
--

INSERT INTO `LastTransfer` (`lastTransfer`) VALUES
('2017-06-27 08:54:07'),
('2017-06-27 08:54:14'),
('2017-06-27 08:54:21'),
('2017-06-27 08:54:27'),
('2017-06-27 08:54:34'),
('2017-06-27 08:58:31'),
('2017-06-27 08:58:37'),
('2017-06-27 08:58:56'),
('2017-06-27 09:03:54'),
('2017-06-27 09:04:01'),
('2017-06-27 09:04:08'),
('2017-06-27 09:04:16'),
('2017-06-27 09:04:24'),
('2017-06-27 09:04:31'),
('2017-06-27 09:04:38'),
('2017-06-27 09:04:43'),
('2017-06-27 09:04:50'),
('2017-06-27 09:04:56'),
('2017-06-27 09:05:03'),
('2017-06-27 09:05:10'),
('2017-06-27 09:05:16'),
('2017-06-27 09:05:23'),
('2017-06-27 09:05:29'),
('2017-06-27 09:05:35'),
('2017-06-27 09:05:42'),
('2017-06-27 09:05:49'),
('2017-06-27 09:05:56'),
('2017-06-27 09:06:03'),
('2017-06-27 09:06:10'),
('2017-06-27 09:06:17'),
('2017-06-27 09:06:24'),
('2017-06-27 09:06:31'),
('2017-06-27 09:06:39'),
('2017-06-27 09:06:46'),
('2017-06-27 09:06:53'),
('2017-06-27 09:07:00'),
('2017-06-27 09:07:07'),
('2017-06-27 09:07:14'),
('2017-06-27 09:07:21'),
('2017-06-27 09:07:27'),
('2017-06-27 09:07:33'),
('2017-06-27 09:07:40'),
('2017-06-27 09:07:47'),
('2017-06-27 09:07:53'),
('2017-06-27 09:07:59'),
('2017-06-27 09:24:07'),
('2017-06-27 09:24:15'),
('2017-06-27 09:24:22'),
('2017-06-27 09:24:31'),
('2017-06-27 09:24:39'),
('2017-06-27 09:24:44'),
('2017-06-27 09:24:51'),
('2017-06-29 07:28:59'),
('2017-06-29 07:29:07'),
('2017-06-29 07:29:17'),
('2017-06-29 07:29:25'),
('2017-06-29 07:29:32'),
('2017-06-29 07:29:40'),
('2017-06-29 07:29:47'),
('2017-06-29 07:29:56'),
('2017-06-29 07:30:03'),
('2017-06-29 07:30:08'),
('2017-06-29 07:30:15'),
('2017-06-29 07:30:22'),
('2017-06-29 07:30:30'),
('2017-06-29 07:30:36'),
('2017-06-29 07:30:43'),
('2017-06-29 07:30:51'),
('2017-06-29 07:30:58'),
('2017-06-29 07:31:05'),
('2017-06-29 07:31:12'),
('2017-06-29 07:31:20'),
('2017-06-29 07:31:28'),
('2017-06-29 07:31:36'),
('2017-06-29 07:31:43'),
('2017-06-29 07:31:50'),
('2017-06-29 07:31:57'),
('2017-06-29 07:32:05'),
('2017-06-29 07:32:12'),
('2017-06-29 07:32:19'),
('2017-06-29 07:32:26'),
('2017-06-29 07:32:33'),
('2017-06-29 07:32:41'),
('2017-06-29 07:32:48'),
('2017-06-29 07:32:56'),
('2017-06-29 07:33:03'),
('2017-06-29 07:33:11'),
('2017-06-29 07:33:19'),
('2017-06-29 07:33:26'),
('2017-06-29 07:33:34'),
('2017-06-29 07:33:41'),
('2017-06-29 07:33:49'),
('2017-06-29 07:33:56'),
('2017-06-29 07:34:03'),
('2017-06-29 07:34:10'),
('2017-06-29 07:34:18'),
('2017-06-29 07:34:26'),
('2017-06-29 07:34:34'),
('2017-06-29 07:34:41'),
('2017-06-29 07:34:49'),
('2017-06-29 07:34:56'),
('2017-06-29 07:35:04'),
('2017-06-29 07:35:12'),
('2017-06-29 07:35:20'),
('2017-06-29 07:35:27'),
('2017-06-29 07:35:35'),
('2017-06-29 07:35:42'),
('2017-06-29 07:35:52'),
('2017-06-29 07:35:58'),
('2017-06-29 07:36:04'),
('2017-06-29 07:36:10'),
('2017-06-29 07:36:15'),
('2017-06-29 07:36:21'),
('2017-06-29 07:36:27'),
('2017-06-29 07:36:33'),
('2017-06-29 07:36:38'),
('2017-06-29 07:36:44'),
('2017-06-29 07:36:50'),
('2017-06-29 07:36:55'),
('2017-06-29 07:37:01'),
('2017-06-29 07:37:07'),
('2017-06-29 07:37:13'),
('2017-06-29 07:37:18'),
('2017-06-29 07:37:24'),
('2017-06-29 07:37:30'),
('2017-06-29 07:37:35'),
('2017-06-29 07:37:41'),
('2017-06-29 07:37:47'),
('2017-06-29 07:37:52'),
('2017-06-29 07:37:58'),
('2017-06-29 07:38:03'),
('2017-06-29 07:38:10'),
('2017-06-29 07:38:15'),
('2017-06-29 07:38:21'),
('2017-06-29 07:38:27'),
('2017-06-29 07:38:33'),
('2017-06-29 07:38:39'),
('2017-06-29 07:38:44'),
('2017-06-29 07:38:50'),
('2017-06-29 07:38:56'),
('2017-06-29 07:39:02'),
('2017-06-29 07:39:08'),
('2017-06-29 07:39:13'),
('2017-06-29 07:39:19'),
('2017-06-29 07:39:25'),
('2017-06-29 07:39:32'),
('2017-06-29 07:39:37'),
('2017-06-29 07:39:43'),
('2017-06-29 07:39:49'),
('2017-06-29 07:39:55'),
('2017-06-29 07:40:01'),
('2017-06-29 07:40:07'),
('2017-06-29 07:40:13'),
('2017-06-29 07:40:19'),
('2017-06-29 07:40:24'),
('2017-06-29 07:40:30'),
('2017-06-29 07:40:36'),
('2017-06-29 07:40:41'),
('2017-06-29 07:40:47'),
('2017-06-29 07:40:53'),
('2017-06-29 07:40:58'),
('2017-06-29 07:41:07'),
('2017-06-29 07:41:14'),
('2017-06-29 07:41:21'),
('2017-06-29 07:41:29'),
('2017-06-29 07:41:36'),
('2017-06-29 07:41:42'),
('2017-06-29 07:41:52'),
('2017-06-29 07:41:57'),
('2017-06-29 07:42:13'),
('2017-06-29 07:42:19'),
('2017-06-29 07:42:26'),
('2017-06-29 07:42:32'),
('2017-06-29 07:42:39'),
('2017-06-29 07:42:48'),
('2017-06-29 07:42:57'),
('2017-06-29 07:43:09'),
('2017-06-29 07:43:15'),
('2017-06-29 07:43:23'),
('2017-06-29 07:43:30'),
('2017-06-29 07:43:38'),
('2017-06-29 07:43:44'),
('2017-06-29 07:43:52'),
('2017-06-29 07:43:58'),
('2017-06-29 07:44:06'),
('2017-06-29 07:44:16'),
('2017-06-29 07:44:23'),
('2017-06-29 07:44:30'),
('2017-06-29 07:44:39'),
('2017-06-29 07:44:46'),
('2017-06-29 07:44:53'),
('2017-06-29 07:45:00'),
('2017-06-29 07:45:08'),
('2017-06-29 07:45:16'),
('2017-06-29 07:45:22'),
('2017-06-29 07:45:30'),
('2017-06-29 07:45:37'),
('2017-06-29 07:45:44'),
('2017-06-29 07:45:52'),
('2017-06-29 07:45:59'),
('2017-06-29 07:46:07'),
('2017-06-29 07:46:14'),
('2017-06-29 07:46:21'),
('2017-06-29 07:46:28'),
('2017-06-29 07:46:35'),
('2017-06-29 07:46:43'),
('2017-06-29 07:46:50'),
('2017-06-29 07:46:57'),
('2017-06-29 07:47:04'),
('2017-06-29 07:47:12'),
('2017-06-29 07:47:20'),
('2017-06-29 07:47:27'),
('2017-06-29 07:47:34'),
('2017-06-29 07:47:41'),
('2017-06-29 07:47:48'),
('2017-06-29 07:47:56'),
('2017-06-29 07:48:03'),
('2017-06-29 07:48:10'),
('2017-06-29 07:48:17'),
('2017-06-29 07:48:25'),
('2017-06-29 07:48:32'),
('2017-06-29 07:48:40'),
('2017-06-29 07:48:47'),
('2017-06-29 07:48:55'),
('2017-06-29 07:49:02'),
('2017-06-29 07:49:09'),
('2017-06-29 07:49:17'),
('2017-06-29 07:49:24'),
('2017-06-29 07:49:30'),
('2017-06-29 07:49:38'),
('2017-06-29 07:49:46'),
('2017-06-29 07:49:53'),
('2017-06-29 07:50:01'),
('2017-06-29 07:50:08'),
('2017-06-29 07:50:15'),
('2017-06-29 07:50:22'),
('2017-06-29 07:50:29'),
('2017-06-29 07:50:38'),
('2017-06-29 07:50:45'),
('2017-06-29 07:50:53'),
('2017-06-29 07:51:00'),
('2017-06-29 07:51:07'),
('2017-06-29 07:51:15'),
('2017-06-29 07:51:25'),
('2017-06-29 07:51:33'),
('2017-06-29 07:51:41'),
('2017-06-29 07:51:48'),
('2017-06-29 07:51:57'),
('2017-06-29 07:52:04'),
('2017-06-29 07:52:13'),
('2017-06-29 07:52:20'),
('2017-06-29 07:52:27'),
('2017-06-29 07:52:36'),
('2017-06-29 07:52:43'),
('2017-06-29 07:52:50'),
('2017-06-29 07:53:01'),
('2017-06-29 07:53:08'),
('2017-06-29 07:53:17'),
('2017-06-29 07:53:23'),
('2017-06-29 07:53:30'),
('2017-06-29 07:53:38'),
('2017-06-29 07:53:47'),
('2017-06-29 07:53:57'),
('2017-06-29 07:54:07'),
('2017-06-29 07:54:15'),
('2017-06-29 07:54:26'),
('2017-06-29 07:54:36'),
('2017-06-29 07:54:45'),
('2017-06-29 07:54:55'),
('2017-06-29 07:55:03'),
('2017-06-29 07:55:13'),
('2017-06-29 07:55:25'),
('2017-06-29 07:55:36'),
('2017-06-29 07:55:44'),
('2017-06-29 07:55:54'),
('2017-06-29 07:56:04'),
('2017-06-29 07:56:14'),
('2017-06-29 07:56:23'),
('2017-06-29 07:56:32'),
('2017-06-29 07:56:45'),
('2017-06-29 07:56:54'),
('2017-06-29 07:57:06'),
('2017-06-29 07:57:14'),
('2017-06-29 07:57:26'),
('2017-06-29 07:57:39'),
('2017-06-29 07:57:46'),
('2017-06-29 07:57:54'),
('2017-06-29 07:58:10'),
('2017-06-29 07:58:19'),
('2017-06-29 07:58:26'),
('2017-06-29 07:58:36'),
('2017-06-29 07:58:47'),
('2017-06-29 07:58:55'),
('2017-06-29 07:59:05'),
('2017-06-29 07:59:14'),
('2017-06-29 07:59:23');

-- --------------------------------------------------------

--
-- Table structure for table `MouseInput`
--

DROP TABLE IF EXISTS `MouseInput`;
CREATE TABLE IF NOT EXISTS `MouseInput` (
  `username` varchar(50) NOT NULL,
  `user` varchar(20) NOT NULL,
  `pid` varchar(10) NOT NULL,
  `start` varchar(10) NOT NULL,
  `xid` varchar(10) NOT NULL,
  `timeChanged` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `type` varchar(10) NOT NULL,
  `xLoc` int(11) NOT NULL,
  `yLoc` int(11) NOT NULL,
  `inputTime` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `insertTimestamp` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`username`,`user`,`pid`,`start`,`xid`,`timeChanged`,`inputTime`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `Process`
--

DROP TABLE IF EXISTS `Process`;
CREATE TABLE IF NOT EXISTS `Process` (
  `username` varchar(50) NOT NULL,
  `user` varchar(20) NOT NULL,
  `pid` varchar(10) NOT NULL,
  `start` varchar(10) NOT NULL,
  `command` text NOT NULL,
  `insertTimestamp` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`username`,`user`,`pid`,`start`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `ProcessArgs`
--

DROP TABLE IF EXISTS `ProcessArgs`;
CREATE TABLE IF NOT EXISTS `ProcessArgs` (
  `username` varchar(50) NOT NULL,
  `user` varchar(20) NOT NULL,
  `pid` varchar(10) NOT NULL,
  `start` varchar(10) NOT NULL,
  `numbered` int(11) NOT NULL,
  `arg` text NOT NULL,
  `insertTimestamp` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`username`,`user`,`pid`,`start`,`numbered`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `ProcessAttributes`
--

DROP TABLE IF EXISTS `ProcessAttributes`;
CREATE TABLE IF NOT EXISTS `ProcessAttributes` (
  `username` varchar(50) NOT NULL,
  `user` varchar(20) NOT NULL,
  `pid` varchar(10) NOT NULL,
  `start` varchar(10) NOT NULL,
  `cpu` decimal(10,0) NOT NULL,
  `mem` decimal(10,0) NOT NULL,
  `vsz` mediumint(9) NOT NULL,
  `rss` mediumint(9) NOT NULL,
  `tty` varchar(10) NOT NULL,
  `stat` varchar(10) NOT NULL,
  `time` varchar(10) NOT NULL,
  `timestamp` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `insertTimestamp` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`username`,`user`,`pid`,`start`,`timestamp`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `Screenshot`
--

DROP TABLE IF EXISTS `Screenshot`;
CREATE TABLE IF NOT EXISTS `Screenshot` (
  `username` varchar(50) NOT NULL,
  `taken` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `screenshot` longblob NOT NULL,
  `insertTimestamp` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`username`,`taken`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `Task`
--

DROP TABLE IF EXISTS `Task`;
CREATE TABLE IF NOT EXISTS `Task` (
  `username` varchar(50) NOT NULL,
  `taskName` varchar(50) NOT NULL,
  `completion` double NOT NULL,
  `insertTimestamp` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`username`,`taskName`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `TaskEvent`
--

DROP TABLE IF EXISTS `TaskEvent`;
CREATE TABLE IF NOT EXISTS `TaskEvent` (
  `username` varchar(50) NOT NULL,
  `taskName` varchar(50) NOT NULL,
  `eventTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `event` varchar(20) NOT NULL,
  `insertTimestamp` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`username`,`taskName`,`eventTime`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `User`
--

DROP TABLE IF EXISTS `User`;
CREATE TABLE IF NOT EXISTS `User` (
  `username` varchar(50) NOT NULL,
  `insertTimestamp` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `UserIP`
--

DROP TABLE IF EXISTS `UserIP`;
CREATE TABLE IF NOT EXISTS `UserIP` (
  `username` varchar(50) NOT NULL,
  `ip` varchar(50) NOT NULL,
  `start` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`username`,`ip`,`start`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `Window`
--

DROP TABLE IF EXISTS `Window`;
CREATE TABLE IF NOT EXISTS `Window` (
  `username` varchar(50) NOT NULL,
  `user` varchar(20) NOT NULL,
  `pid` varchar(10) NOT NULL,
  `start` varchar(10) NOT NULL,
  `xid` varchar(10) NOT NULL,
  `firstClass` varchar(20) NOT NULL,
  `secondClass` varchar(20) NOT NULL,
  `insertTimestamp` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`username`,`user`,`pid`,`start`,`xid`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `WindowDetails`
--

DROP TABLE IF EXISTS `WindowDetails`;
CREATE TABLE IF NOT EXISTS `WindowDetails` (
  `username` varchar(50) NOT NULL,
  `user` varchar(20) NOT NULL,
  `pid` varchar(10) NOT NULL,
  `start` varchar(10) NOT NULL,
  `xid` varchar(10) NOT NULL,
  `x` int(11) NOT NULL,
  `y` int(11) NOT NULL,
  `width` int(11) NOT NULL,
  `height` int(11) NOT NULL,
  `name` text NOT NULL,
  `timeChanged` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `insertTimestamp` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`username`,`user`,`pid`,`start`,`xid`,`timeChanged`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `KeyboardInput`
--
ALTER TABLE `KeyboardInput`
  ADD CONSTRAINT `KeyboardInput_ibfk_1` FOREIGN KEY (`username`,`user`,`pid`,`start`,`xid`,`timeChanged`) REFERENCES `WindowDetails` (`username`, `user`, `pid`, `start`, `xid`, `timeChanged`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `MouseInput`
--
ALTER TABLE `MouseInput`
  ADD CONSTRAINT `MouseInput_ibfk_1` FOREIGN KEY (`username`,`user`,`pid`,`start`,`xid`,`timeChanged`) REFERENCES `WindowDetails` (`username`, `user`, `pid`, `start`, `xid`, `timeChanged`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `Process`
--
ALTER TABLE `Process`
  ADD CONSTRAINT `Process_ibfk_1` FOREIGN KEY (`username`) REFERENCES `User` (`username`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `ProcessArgs`
--
ALTER TABLE `ProcessArgs`
  ADD CONSTRAINT `ProcessArgs_ibfk_1` FOREIGN KEY (`username`,`user`,`pid`,`start`) REFERENCES `Process` (`username`, `user`, `pid`, `start`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `ProcessAttributes`
--
ALTER TABLE `ProcessAttributes`
  ADD CONSTRAINT `ProcessAttributes_ibfk_1` FOREIGN KEY (`username`,`user`,`pid`,`start`) REFERENCES `Process` (`username`, `user`, `pid`, `start`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `Screenshot`
--
ALTER TABLE `Screenshot`
  ADD CONSTRAINT `Screenshot_ibfk_1` FOREIGN KEY (`username`) REFERENCES `User` (`username`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `Task`
--
ALTER TABLE `Task`
  ADD CONSTRAINT `Task_ibfk_1` FOREIGN KEY (`username`) REFERENCES `User` (`username`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `TaskEvent`
--
ALTER TABLE `TaskEvent`
  ADD CONSTRAINT `TaskEvent_ibfk_1` FOREIGN KEY (`username`,`taskName`) REFERENCES `Task` (`username`, `taskName`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `UserIP`
--
ALTER TABLE `UserIP`
  ADD CONSTRAINT `UserIP_ibfk_1` FOREIGN KEY (`username`) REFERENCES `User` (`username`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `Window`
--
ALTER TABLE `Window`
  ADD CONSTRAINT `Window_ibfk_1` FOREIGN KEY (`username`,`user`,`pid`,`start`) REFERENCES `Process` (`username`, `user`, `pid`, `start`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `WindowDetails`
--
ALTER TABLE `WindowDetails`
  ADD CONSTRAINT `WindowDetails_ibfk_1` FOREIGN KEY (`username`,`user`,`pid`,`start`,`xid`) REFERENCES `Window` (`username`, `user`, `pid`, `start`, `xid`) ON DELETE CASCADE ON UPDATE CASCADE;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
