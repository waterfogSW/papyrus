CREATE TABLE product
(
    id      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title   VARCHAR(255) NOT NULL,
    content TEXT         NOT NULL
);
CREATE UNIQUE INDEX Product_title_uindex ON product (title);
