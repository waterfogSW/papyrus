CREATE TABLE product
(
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL
);
CREATE UNIQUE INDEX Product_name_uindex ON product (name);
