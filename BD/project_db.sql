-- SQL Script Completo en Español para Base de Datos de "Steam Autónomo"
-- Autor: Antigravity AI
-- Versión compatible con XAMPP (MySQL / MariaDB)

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Crear y seleccionar la base de datos
--
CREATE DATABASE IF NOT EXISTS `project_db_extended`;
USE `project_db_extended`;

-- --------------------------------------------------------
-- 1. TABLA: `paises`
-- --------------------------------------------------------
DROP TABLE IF EXISTS `paises`;
CREATE TABLE `paises` (
  `codigo_pais` varchar(10) NOT NULL,
  `nombre_pais` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`codigo_pais`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `paises` (`codigo_pais`, `nombre_pais`) VALUES
('au', 'Australia'),
('br', 'Brasil'),
('ca', 'Canada'),
('cl', 'Chile'),
('cn', 'China'),
('es', 'Espana'),
('in', 'India'),
('mx', 'Mexico'),
('pe', 'Perú'),
('tr', 'Turquia'),
('us', 'Estados Unidos');

-- --------------------------------------------------------
-- 2. TABLA: `monedas`
-- --------------------------------------------------------
DROP TABLE IF EXISTS `monedas`;
CREATE TABLE `monedas` (
  `codigo_moneda` varchar(10) NOT NULL,
  `tasa_conversion_a_usd` double DEFAULT NULL,
  PRIMARY KEY (`codigo_moneda`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `monedas` (`codigo_moneda`, `tasa_conversion_a_usd`) VALUES
('AUD', 0.65),
('BRL', 0.19),
('CAD', 0.73),
('CLP', 0.0011),
('CNY', 0.14),
('EUR', 1.08),
('INR', 0.012),
('MXN', 0.059),
('PEN', 0.27),
('TRY', 0.031),
('USD', 1.00);

-- --------------------------------------------------------
-- 3. TABLA: `juegos`
-- --------------------------------------------------------
DROP TABLE IF EXISTS `juegos`;
CREATE TABLE `juegos` (
  `id` int(11) NOT NULL,
  `nombre` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `juegos` (`id`, `nombre`) VALUES
(319510, "Five Nights at Freddy's"),
(550, 'Left 4 Dead 2'),
(570, 'Dota 2'),
(730, 'Counter-Strike 2'),
(105600, 'Terraria'),
(252490, 'Rust'),
(271590, 'Grand Theft Auto V Enhanced'),
(292030, 'The Witcher 3: Wild Hunt'),
(413150, 'Stardew Valley'),
(1091500, 'Cyberpunk 2077'),
(1245620, 'ELDEN RING');

-- --------------------------------------------------------
-- 4. TABLA: `usuarios`
-- --------------------------------------------------------
DROP TABLE IF EXISTS `usuarios`;
CREATE TABLE `usuarios` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre_usuario` varchar(50) NOT NULL UNIQUE,
  `contrasena` varchar(255) NOT NULL,
  `correo` varchar(100) NOT NULL UNIQUE,
  `saldo_billetera` decimal(10,2) DEFAULT 0.00,
  `codigo_pais` varchar(10) NOT NULL DEFAULT 'cl',
  `creado_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_usuario_pais` FOREIGN KEY (`codigo_pais`) REFERENCES `paises` (`codigo_pais`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Usuarios de prueba con contraseñas cifradas en MD5 (ejemplo: '123456' -> 'e10adc3949ba59abbe56e057f20f883e')
INSERT INTO `usuarios` (`id`, `nombre_usuario`, `contrasena`, `correo`, `saldo_billetera`, `codigo_pais`) VALUES
(1, 'gabeneuell', 'e10adc3949ba59abbe56e057f20f883e', 'gabe@valvesoftware.com', 9999.99, 'us'),
(2, 'migue_gamer', 'e10adc3949ba59abbe56e057f20f883e', 'migue@ejemplo.com', 50.00, 'cl'),
(3, 'chilean_pro', 'e10adc3949ba59abbe56e057f20f883e', 'chilean@ejemplo.com', 12.50, 'cl'),
(4, 'rust_fanatic', 'e10adc3949ba59abbe56e057f20f883e', 'rust@ejemplo.com', 0.00, 'cl');

-- --------------------------------------------------------
-- 5. TABLA: `bibliotecas`
-- --------------------------------------------------------
DROP TABLE IF EXISTS `bibliotecas`;
CREATE TABLE `bibliotecas` (
  `id_usuario` int(11) NOT NULL,
  `id_juego` int(11) NOT NULL,
  `minutos_jugados` int(11) DEFAULT 0,
  `fecha_compra` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id_usuario`, `id_juego`),
  CONSTRAINT `fk_bib_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_bib_juego` FOREIGN KEY (`id_juego`) REFERENCES `juegos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `bibliotecas` (`id_usuario`, `id_juego`, `minutos_jugados`) VALUES
(1, 319510, 3600),
(1, 550, 12000),
(1, 730, 99999),
(2, 319510, 180),
(2, 550, 4800),
(2, 105600, 300),
(3, 319510, 450),
(3, 550, 7200),
(3, 1245620, 90),
(4, 550, 24000),
(4, 252490, 80000);

-- --------------------------------------------------------
-- 6. TABLA: `compras`
-- --------------------------------------------------------
DROP TABLE IF EXISTS `compras`;
CREATE TABLE `compras` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `id_usuario` int(11) NOT NULL,
  `id_juego` int(11) NOT NULL,
  `monto_pagado` decimal(10,2) NOT NULL,
  `fecha_compra` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_compra_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_compra_juego` FOREIGN KEY (`id_juego`) REFERENCES `juegos` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `compras` (`id`, `id_usuario`, `id_juego`, `monto_pagado`) VALUES
(1, 2, 319510, 9.99),
(2, 2, 550, 9.99),
(3, 2, 105600, 4.99),
(4, 3, 319510, 9.99),
(5, 3, 550, 9.99),
(6, 3, 1245620, 59.99),
(7, 4, 550, 9.99),
(8, 4, 252490, 39.99);

-- --------------------------------------------------------
-- 7. TABLA: `amistades`
-- --------------------------------------------------------
DROP TABLE IF EXISTS `amistades`;
CREATE TABLE `amistades` (
  `id_usuario1` int(11) NOT NULL,
  `id_usuario2` int(11) NOT NULL,
  `estado` varchar(20) DEFAULT 'accepted',
  `establecida_en` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id_usuario1`,`id_usuario2`),
  CONSTRAINT `fk_amigo1` FOREIGN KEY (`id_usuario1`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_amigo2` FOREIGN KEY (`id_usuario2`) REFERENCES `usuarios` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `amistades` (`id_usuario1`, `id_usuario2`, `estado`) VALUES
(1, 2, 'accepted'),
(2, 3, 'accepted'),
(2, 4, 'accepted'),
(3, 4, 'accepted');

COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;

