-- EHPS reference data seed (PostgreSQL dialect)
-- Kept in lockstep with src/test/resources/db/h2/data.sql.
-- Machine types + parameter metadata only (BEHAVIOR-BASELINE.md §6, §9). No users/machines seeded here.
-- good_desc/warning_desc/bad_desc text is byte-faithful to legacy TechnicianCheckupService.getParameterDetails().

INSERT INTO machine_types (type_id, type_name, code_prefix, speciality, param_count) VALUES
    (1, 'Lithography',   'LH',  'lithography',    5),
    (2, 'Etcher',        'EH',  'etcher',         5),
    (3, 'CVD',           'CVD', 'cvd',            4),
    (4, 'Ion Implanter', 'ION', 'ion_implanter',  4),
    (5, 'CMP',           'CMP', 'cmp',            4),
    (6, 'Inspection',    'INS', 'inspection',     4);

-- Type 1: Lithography
INSERT INTO machine_type_parameters (type_id, param_index, param_name, unit, good_desc, warning_desc, bad_desc) VALUES
    (1, 1, 'Light Intensity',         'mW/cm²', '80 - 100', '60 - 79', '< 60'),
    (1, 2, 'Lens Temperature',        '°C',     '20 - 25',  '26 - 28', '> 28'),
    (1, 3, 'Stage Vibration',         'nm',     '< 3',      '3 - 5',   '> 5'),
    (1, 4, 'Reticle Alignment Error', 'nm',     '< 2',      '2 - 4',   '> 4'),
    (1, 5, 'Focus Accuracy',          'nm',     '< 10',     '10 - 20', '> 20');

-- Type 2: Etcher
INSERT INTO machine_type_parameters (type_id, param_index, param_name, unit, good_desc, warning_desc, bad_desc) VALUES
    (2, 1, 'RF Plasma Power',    'Watts',  '450 - 500', '400 - 449', '< 400'),
    (2, 2, 'Chamber Pressure',   'mTorr',  '20 - 30',   '31 - 40',   '> 40'),
    (2, 3, 'Chamber Temperature','°C',     '60 - 80',   '81 - 90',   '> 90'),
    (2, 4, 'Gas Flow Rate',      'sccm',   '80 - 100',  '60 - 79',   '< 60'),
    (2, 5, 'Etch Rate',          'nm/min', '100 - 120', '85 - 99',   '< 85');

-- Type 3: CVD
INSERT INTO machine_type_parameters (type_id, param_index, param_name, unit, good_desc, warning_desc, bad_desc) VALUES
    (3, 1, 'Chamber Temperature',            '°C',   '300 - 350', '351 - 370', '> 370'),
    (3, 2, 'Gas Flow Rate',                  'sccm', '100 - 150', '80 - 99',   '< 80'),
    (3, 3, 'Vacuum Pressure',                'Torr', '0.5 - 1',   '1 - 2',     '> 2'),
    (3, 4, 'Deposition Thickness Uniformity','%',    '> 95',      '90 - 95',   '< 90');

-- Type 4: Ion Implanter
INSERT INTO machine_type_parameters (type_id, param_index, param_name, unit, good_desc, warning_desc, bad_desc) VALUES
    (4, 1, 'Beam Current',       'mA',   '10 - 12',  '8 - 9',   '< 8'),
    (4, 2, 'Beam Energy',        'keV',  '40 - 50',  '30 - 39', '< 30'),
    (4, 3, 'Vacuum Pressure',    'Torr', '0.000001', '0.00001', '> 0.0001'),
    (4, 4, 'Cooling Temperature','°C',   '18 - 22',  '23 - 25', '> 25');

-- Type 5: CMP
INSERT INTO machine_type_parameters (type_id, param_index, param_name, unit, good_desc, warning_desc, bad_desc) VALUES
    (5, 1, 'Slurry Flow Rate', 'ml/min', '150 - 180', '120 - 149', '< 120'),
    (5, 2, 'Pad Pressure',     'psi',    '3 - 5',     '> 5 - 6',   '> 6'),
    (5, 3, 'Platen Speed',     'RPM',    '60 - 80',   '81 - 95',   '> 95'),
    (5, 4, 'Pad Temperature',  '°C',     '25 - 35',   '36 - 40',   '> 40');

-- Type 6: Inspection
INSERT INTO machine_type_parameters (type_id, param_index, param_name, unit, good_desc, warning_desc, bad_desc) VALUES
    (6, 1, 'Laser Power',              '%',       '90 - 100', '75 - 89', '< 75'),
    (6, 2, 'Sensor Calibration Offset','nm',      '0 - 2',    '3 - 5',   '> 5'),
    (6, 3, 'Focus Error',              'nm',      '0 - 10',   '11 - 20', '> 20'),
    (6, 4, 'Defect Count per Wafer',   'defects', '< 10',     '10 - 50', '> 50');
