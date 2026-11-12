-- Local starter image catalogue imported from the supplied MedicSystem images folder.
-- New entries are display-only: price and stock remain zero until the pharmacy configures them.
INSERT INTO categories(name) SELECT 'Skin care' WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name='Skin care');
INSERT INTO categories(name) SELECT 'Vitamins & supplements' WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name='Vitamins & supplements');
INSERT INTO categories(name) SELECT 'Cold & allergy' WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name='Cold & allergy');
INSERT INTO categories(name) SELECT 'Pain relief' WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name='Pain relief');
INSERT INTO categories(name) SELECT 'First aid' WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name='First aid');
INSERT INTO manufacturers(name) SELECT 'Imported demo catalogue' WHERE NOT EXISTS (SELECT 1 FROM manufacturers WHERE name='Imported demo catalogue');

UPDATE medicines SET image_url='/assets/catalog/vitamin-c.jpg' WHERE name='Vitamin C 500 mg' AND (image_url IS NULL OR image_url='');
UPDATE medicines SET image_url='/assets/catalog/paracetamol.jpg' WHERE name='Paracetamol 500 mg' AND (image_url IS NULL OR image_url='');
UPDATE medicines SET image_url='/assets/catalog/cetirizine.jpg' WHERE name='Cetirizine 10 mg' AND (image_url IS NULL OR image_url='');

INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Skin care'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Aloe Vera Gel','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/aloe-vera.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Aloe Vera Gel');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Pain relief'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Aspirin Tablets','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/aspirin.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Aspirin Tablets');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Skin care'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Calamine Lotion','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/calamine.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Calamine Lotion');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Vitamins & supplements'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Calcium Supplement','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/calcium.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Calcium Supplement');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Skin care'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Clotrimazole Cream','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/clotrimazole.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Clotrimazole Cream');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Cold & allergy'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Cold Relief Product','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/cold-relief.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Cold Relief Product');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Cold & allergy'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Cough Syrup','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/cough-syrup.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Cough Syrup');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Pain relief'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Diclofenac Gel','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/diclofenac-gel.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Diclofenac Gel');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Pain relief'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Ibuprofen Tablets','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/ibuprofen.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Ibuprofen Tablets');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Vitamins & supplements'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Iron Supplement','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/iron-tablets.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Iron Supplement');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Cold & allergy'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Levocetirizine','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/levocetirizine.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Levocetirizine');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Cold & allergy'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Montelukast Tablets','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/montelukast.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Montelukast Tablets');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Skin care'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Moisturizing Cream','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/moisturizing-cream.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Moisturizing Cream');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Vitamins & supplements'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Multivitamin Tablets','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/multivitamin.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Multivitamin Tablets');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Cold & allergy'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Nasal Drops','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/nasal-drops.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Nasal Drops');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Pain relief'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Pain Relief Balm','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/pain-balm.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Pain Relief Balm');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Pain relief'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Paracetamol 650 mg','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/paracetamol-650.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Paracetamol 650 mg');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='First aid'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Antiseptic Cream','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/antiseptic.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Antiseptic Cream');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='First aid'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Antiseptic Lotion','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/antiseptic-lotion.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Antiseptic Lotion');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Vitamins & supplements'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Vitamin B12 Supplement','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/vitamin-b12.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Vitamin B12 Supplement');
INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,image_url,active,stock_quantity,min_stock)
SELECT (SELECT id FROM categories WHERE name='Vitamins & supplements'),(SELECT id FROM manufacturers WHERE name='Imported demo catalogue'),'Vitamin D3 Supplement','Display-only starter listing. Confirm package, price, directions, and prescription status with a pharmacist before sale.',0.00,FALSE,'/assets/catalog/vitamin-d3.jpg',TRUE,0,5
WHERE NOT EXISTS (SELECT 1 FROM medicines WHERE name='Vitamin D3 Supplement');
