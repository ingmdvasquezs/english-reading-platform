-- =============================================================================
-- V25: Seed Reading Comprehension Quizzes for 6 Authorized Platform Readings
-- Levels: A1, A2, B1, B2, C1, C2
-- Rules: 3 questions per reading (1 FACTUAL, 1 INFERENCE, 1 MAIN_IDEA)
--        4 options per question, exactly 1 correct option
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Reading 1: A1 - The Lost Blue Scarf (20000000-0000-0000-0000-000000000001)
-- -----------------------------------------------------------------------------
INSERT INTO reading_comprehension_questions (id, reading_id, ordinal, question_type, prompt, explanation, created_at)
VALUES
    ('a0000000-0000-0000-0001-000000000001', '20000000-0000-0000-0000-000000000001', 1, 'FACTUAL',
     'What did Nora and her brother buy at the bakery?',
     'The text explicitly mentions that Nora and Leo bought warm bread and two small cakes at the bakery.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0001-000000000002', '20000000-0000-0000-0000-000000000001', 2, 'INFERENCE',
     'Why did Nora take special care to wash the scarf as soon as she returned home?',
     'The scarf was made by her grandmother and held sentimental value, so she wanted to restore it after finding it wet and dirty.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0001-000000000003', '20000000-0000-0000-0000-000000000001', 3, 'MAIN_IDEA',
     'What is the story mainly about?',
     'The whole narrative recounts how Nora lost her cherished scarf during an errand and recovered it on the street with her brother''s help.',
     CURRENT_TIMESTAMP);

INSERT INTO reading_comprehension_options (id, question_id, ordinal, content, is_correct)
VALUES
    ('b0000000-0000-0000-0001-000000000101', 'a0000000-0000-0000-0001-000000000001', 1, 'Fresh milk and apples', false),
    ('b0000000-0000-0000-0001-000000000102', 'a0000000-0000-0000-0001-000000000001', 2, 'Warm bread and two small cakes', true),
    ('b0000000-0000-0000-0001-000000000103', 'a0000000-0000-0000-0001-000000000001', 3, 'Hot tea and butter biscuits', false),
    ('b0000000-0000-0000-0001-000000000104', 'a0000000-0000-0000-0001-000000000001', 4, 'Chocolate cookies and sandwiches', false),

    ('b0000000-0000-0000-0001-000000000201', 'a0000000-0000-0000-0001-000000000002', 1, 'She wanted to give it as a present to the baker.', false),
    ('b0000000-0000-0000-0001-000000000202', 'a0000000-0000-0000-0001-000000000002', 2, 'She cared deeply about the handmade gift from her grandmother.', true),
    ('b0000000-0000-0000-0001-000000000203', 'a0000000-0000-0000-0001-000000000002', 3, 'Her brother Leo refused to walk with her unless it was clean.', false),
    ('b0000000-0000-0000-0001-000000000204', 'a0000000-0000-0000-0001-000000000002', 4, 'She needed to return it to the flower shop owner.', false),

    ('b0000000-0000-0000-0001-000000000301', 'a0000000-0000-0000-0001-000000000003', 1, 'How a baker prepares fresh bread on Monday mornings', false),
    ('b0000000-0000-0000-0001-000000000302', 'a0000000-0000-0000-0001-000000000003', 2, 'A girl losing her favorite scarf and finding it on the street', true),
    ('b0000000-0000-0000-0001-000000000303', 'a0000000-0000-0000-0001-000000000003', 3, 'Two children adopting a stray dog near a park bench', false),
    ('b0000000-0000-0000-0001-000000000304', 'a0000000-0000-0000-0001-000000000003', 4, 'Learning how to knit wool clothing at home', false);

-- -----------------------------------------------------------------------------
-- Reading 2: A2 - A Quiet Morning by the River (20000000-0000-0000-0000-000000000006)
-- -----------------------------------------------------------------------------
INSERT INTO reading_comprehension_questions (id, reading_id, ordinal, question_type, prompt, explanation, created_at)
VALUES
    ('a0000000-0000-0000-0002-000000000001', '20000000-0000-0000-0000-000000000006', 1, 'FACTUAL',
     'What did Priya and her father do when they found plastic cups near the river path?',
     'The text says that they put the rubbish into their basket instead of leaving it behind for someone else.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0002-000000000002', '20000000-0000-0000-0000-000000000006', 2, 'INFERENCE',
     'Why was Priya pleased with their list even though she was cold and wet from the rain?',
     'She knew their list of birds was useful for the community nature project.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0002-000000000003', '20000000-0000-0000-0000-000000000006', 3, 'MAIN_IDEA',
     'What is this story mostly about?',
     'The story shows Priya and her father helping a community project by counting birds and cleaning up the river path.',
     CURRENT_TIMESTAMP);

INSERT INTO reading_comprehension_options (id, question_id, ordinal, content, is_correct)
VALUES
    ('b0000000-0000-0000-0002-000000010001', 'a0000000-0000-0000-0002-000000000001', 1, 'They asked a park worker to pick up the rubbish.', false),
    ('b0000000-0000-0000-0002-000000010002', 'a0000000-0000-0000-0002-000000000001', 2, 'They placed the rubbish in their basket to take away.', true),
    ('b0000000-0000-0000-0002-000000010003', 'a0000000-0000-0000-0002-000000000001', 3, 'They buried the plastic cups under the willow tree.', false),
    ('b0000000-0000-0000-0002-000000010004', 'a0000000-0000-0000-0002-000000000001', 4, 'They left the cups by the bridge for another person to collect.', false),

    ('b0000000-0000-0000-0002-000000020001', 'a0000000-0000-0000-0002-000000000002', 1, 'She knew their list of birds would help the community nature project.', true),
    ('b0000000-0000-0000-0002-000000020002', 'a0000000-0000-0000-0002-000000000002', 2, 'Her father promised to buy her a new notebook at the market.', false),
    ('b0000000-0000-0000-0002-000000020003', 'a0000000-0000-0000-0002-000000000002', 3, 'The project leader offered to pay money for counting birds.', false),
    ('b0000000-0000-0000-0002-000000020004', 'a0000000-0000-0000-0002-000000000002', 4, 'She was happy that the rain stopped them from walking more.', false),

    ('b0000000-0000-0000-0002-000000030001', 'a0000000-0000-0000-0002-000000000003', 1, 'The danger of walking near the river in the early morning', false),
    ('b0000000-0000-0000-0002-000000030002', 'a0000000-0000-0000-0002-000000000003', 2, 'A father and daughter watching birds to help a community nature project', true),
    ('b0000000-0000-0000-0002-000000030003', 'a0000000-0000-0000-0002-000000000003', 3, 'How heavy rain stops people from fishing in the river', false),
    ('b0000000-0000-0000-0002-000000030004', 'a0000000-0000-0000-0002-000000000003', 4, 'Ways to take pictures of wild birds by the water', false);

-- -----------------------------------------------------------------------------
-- Reading 3: B1 - Learning to Ask Better Questions (20000000-0000-0000-0000-000000000011)
-- -----------------------------------------------------------------------------
INSERT INTO reading_comprehension_questions (id, reading_id, ordinal, question_type, prompt, explanation, created_at)
VALUES
    ('a0000000-0000-0000-0003-000000000001', '20000000-0000-0000-0000-000000000011', 1, 'FACTUAL',
     'What was the actual cause of the bent bicycle wheel in Nadia''s workshop class?',
     'The instructor showed that a loose spoke caused the wheel to bend, so only an adjustment was needed instead of a new wheel.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0003-000000000002', '20000000-0000-0000-0000-000000000011', 2, 'INFERENCE',
     'Why did Nadia avoid giving her struggling colleague an immediate answer?',
     'She wanted to help him think through the problem and discover the missing detail himself.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0003-000000000003', '20000000-0000-0000-0000-000000000011', 3, 'MAIN_IDEA',
     'What main lesson did Nadia learn from her experience?',
     'Asking careful questions instead of guessing quickly helped her solve problems and guide other learners.',
     CURRENT_TIMESTAMP);

INSERT INTO reading_comprehension_options (id, question_id, ordinal, content, is_correct)
VALUES
    ('b0000000-0000-0000-0003-000000010001', 'a0000000-0000-0000-0003-000000000001', 1, 'A punctured rubber tire', false),
    ('b0000000-0000-0000-0003-000000010002', 'a0000000-0000-0000-0003-000000000001', 2, 'A loose spoke in the wheel', true),
    ('b0000000-0000-0000-0003-000000010003', 'a0000000-0000-0000-0003-000000000001', 3, 'A broken pedal axle', false),
    ('b0000000-0000-0000-0003-000000010004', 'a0000000-0000-0000-0003-000000000001', 4, 'A cracked metal rim', false),

    ('b0000000-0000-0000-0003-000000020001', 'a0000000-0000-0000-0003-000000000002', 1, 'She wanted him to examine the steps and discover the missing detail himself.', true),
    ('b0000000-0000-0000-0003-000000020002', 'a0000000-0000-0000-0003-000000000002', 2, 'She was unfamiliar with the technical task and needed time to consult her supervisor.', false),
    ('b0000000-0000-0000-0003-000000020003', 'a0000000-0000-0000-0003-000000000002', 3, 'Her team had strict rules against helping coworkers during the day.', false),
    ('b0000000-0000-0000-0003-000000020004', 'a0000000-0000-0000-0003-000000000002', 4, 'She wanted him to learn bicycle repair instead of office procedures.', false),

    ('b0000000-0000-0000-0003-000000030001', 'a0000000-0000-0000-0003-000000000003', 1, 'Replacing broken parts is usually faster than trying to repair them.', false),
    ('b0000000-0000-0000-0003-000000030002', 'a0000000-0000-0000-0003-000000000003', 2, 'Asking careful questions and being patient can turn uncertainty into a useful way to learn.', true),
    ('b0000000-0000-0000-0003-000000030003', 'a0000000-0000-0000-0003-000000000003', 3, 'Customer support calls should be kept as short as possible to save time.', false),
    ('b0000000-0000-0000-0003-000000030004', 'a0000000-0000-0000-0003-000000000003', 4, 'Working with tools is necessary for anyone who wants a promotion at work.', false);

-- -----------------------------------------------------------------------------
-- Reading 4: B2 - The Cost of Constant Attention (20000000-0000-0000-0000-000000000013)
-- -----------------------------------------------------------------------------
INSERT INTO reading_comprehension_questions (id, reading_id, ordinal, question_type, prompt, explanation, created_at)
VALUES
    ('a0000000-0000-0000-0004-000000000001', '20000000-0000-0000-0000-000000000013', 1, 'FACTUAL',
     'Which concrete action did Inez take as part of her personal attention experiment?',
     'The passage notes that she removed alerts that required no immediate action, moved social apps off the home screen, and set two periods for messages.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0004-000000000002', '20000000-0000-0000-0000-000000000013', 2, 'INFERENCE',
     'What does the colleague''s failure to adopt Inez''s exact routine illustrate about managing digital habits?',
     'Because his role required rapid replies, his experience showed that attention strategies must be tailored to specific professional duties rather than applied universally.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0004-000000000003', '20000000-0000-0000-0000-000000000013', 3, 'MAIN_IDEA',
     'What is the central insight argued throughout the passage?',
     'The text emphasizes that while individuals can adopt intentional habits, attention is also fundamentally constrained by digital platforms engineered to maximize engagement time.',
     CURRENT_TIMESTAMP);

INSERT INTO reading_comprehension_options (id, question_id, ordinal, content, is_correct)
VALUES
    ('b0000000-0000-0000-0004-000000000101', 'a0000000-0000-0000-0004-000000000001', 1, 'She replaced her smartphone with a basic device that had no internet capability.', false),
    ('b0000000-0000-0000-0004-000000000102', 'a0000000-0000-0000-0004-000000000001', 2, 'She eliminated non-urgent notifications and scheduled dedicated periods for messages.', true),
    ('b0000000-0000-0000-0004-000000000103', 'a0000000-0000-0000-0004-000000000001', 3, 'She completely stopped reading journalism and canceled all her news subscriptions.', false),
    ('b0000000-0000-0000-0004-000000000104', 'a0000000-0000-0000-0004-000000000001', 4, 'She deleted all digital communication software from her workplace computer.', false),

    ('b0000000-0000-0000-0004-000000000201', 'a0000000-0000-0000-0004-000000000002', 1, 'Attention practices must be adapted to differing job roles and responsibilities rather than applied universally.', true),
    ('b0000000-0000-0000-0004-000000000202', 'a0000000-0000-0000-0004-000000000002', 2, 'Employees without technical backgrounds are incapable of reducing digital distractions.', false),
    ('b0000000-0000-0000-0004-000000000203', 'a0000000-0000-0000-0004-000000000002', 3, 'Personal willpower is the sole determinant of whether an employee maintains workplace concentration.', false),
    ('b0000000-0000-0000-0004-000000000204', 'a0000000-0000-0000-0004-000000000002', 4, 'Emergency communication channels make attention management completely unworkable in corporate teams.', false),

    ('b0000000-0000-0000-0004-000000000301', 'a0000000-0000-0000-0004-000000000003', 1, 'Digital disconnection is mandatory because all software applications harm cognitive health.', false),
    ('b0000000-0000-0000-0004-000000000302', 'a0000000-0000-0000-0004-000000000003', 2, 'Regaining focus requires personal boundaries alongside an awareness of systemic platform designs that compete for user time.', true),
    ('b0000000-0000-0000-0004-000000000303', 'a0000000-0000-0000-0004-000000000003', 3, 'Morning routines are the only time of day when information intake can be effectively managed.', false),
    ('b0000000-0000-0000-0004-000000000304', 'a0000000-0000-0000-0004-000000000003', 4, 'Workplace productivity will automatically improve if organizations ban mobile devices entirely.', false);

-- -----------------------------------------------------------------------------
-- Reading 5: C1 - The Museum of Unfinished Things (20000000-0000-0000-0000-000000000017)
-- -----------------------------------------------------------------------------
INSERT INTO reading_comprehension_questions (id, reading_id, ordinal, question_type, prompt, explanation, created_at)
VALUES
    ('a0000000-0000-0000-0005-000000000001', '20000000-0000-0000-0000-000000000017', 1, 'FACTUAL',
     'How did the museum adapt its collection after a visitor challenged the representation of institutional archives?',
     'The text explicitly says that the museum invited residents to document abandoned local projects, including the reasons they ended.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0005-000000000002', '20000000-0000-0000-0000-000000000017', 2, 'INFERENCE',
     'What distinction does Dr. Vale establish between mere failure and productive knowledge?',
     'Dr. Vale argues that failure in itself is not inherently wise or productive; only deliberate reflection, evidence, and accountability convert an unsuccessful attempt into meaningful understanding.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0005-000000000003', '20000000-0000-0000-0000-000000000017', 3, 'MAIN_IDEA',
     'What core philosophical premise underlies the exhibition at the railway warehouse?',
     'The central theme explores how incomplete objects reveal hidden design decisions, demonstrating that an invention''s value may reside in the lateral inquiries it generates rather than its original utility.',
     CURRENT_TIMESTAMP);

INSERT INTO reading_comprehension_options (id, question_id, ordinal, content, is_correct)
VALUES
    ('b0000000-0000-0000-0005-000000000101', 'a0000000-0000-0000-0005-000000000001', 1, 'It purchased discarded mechanical prototypes from foreign engineering universities.', false),
    ('b0000000-0000-0000-0005-000000000102', 'a0000000-0000-0000-0005-000000000001', 2, 'It invited local residents to document abandoned community projects and the circumstances surrounding their end.', true),
    ('b0000000-0000-0000-0005-000000000103', 'a0000000-0000-0000-0005-000000000001', 3, 'It eliminated the clock exhibit to make space for successful historical patents.', false),
    ('b0000000-0000-0000-0005-000000000104', 'a0000000-0000-0000-0005-000000000001', 4, 'It restricted future displays to commercially manufactured domestic furniture.', false),

    ('b0000000-0000-0000-0005-000000000201', 'a0000000-0000-0000-0005-000000000002', 1, 'Unsuccessful attempts only yield value when examined through structured reflection, empirical evidence, and accountability.', true),
    ('b0000000-0000-0000-0005-000000000202', 'a0000000-0000-0000-0005-000000000002', 2, 'Every technical mistake inevitably produces breakthroughs if designers are given sufficient financial capital.', false),
    ('b0000000-0000-0000-0005-000000000203', 'a0000000-0000-0000-0005-000000000002', 3, 'Unfinished artifacts are ethically superior to finished consumer goods because they resist commercialization.', false),
    ('b0000000-0000-0000-0005-000000000204', 'a0000000-0000-0000-0005-000000000002', 4, 'The public should celebrate failure unconditionally regardless of the material harm it generates.', false),

    ('b0000000-0000-0000-0005-000000000301', 'a0000000-0000-0000-0005-000000000003', 1, 'Industrial warehouses are poorly suited for preserving delicate domestic artifacts and architectural models.', false),
    ('b0000000-0000-0000-0005-000000000302', 'a0000000-0000-0000-0005-000000000003', 2, 'Exhibiting incomplete work exposes the compromises behind finished objects and reveals that value can emerge beyond original functional intentions.', true),
    ('b0000000-0000-0000-0005-000000000303', 'a0000000-0000-0000-0005-000000000003', 3, 'Technological progress is purely linear and rendered obsolete whenever community projects are abandoned.', false),
    ('b0000000-0000-0000-0005-000000000304', 'a0000000-0000-0000-0005-000000000003', 4, 'Civic funding should be diverted exclusively to artistic experiments that have achieved commercial success.', false);

-- -----------------------------------------------------------------------------
-- Reading 6: C2 - The Inheritance of Dust (30000000-0000-0000-0000-000000000048)
-- -----------------------------------------------------------------------------
INSERT INTO reading_comprehension_questions (id, reading_id, ordinal, question_type, prompt, explanation, created_at)
VALUES
    ('a0000000-0000-0000-0006-000000000001', '30000000-0000-0000-0000-000000000048', 1, 'FACTUAL',
     'Under what specific arrangement does the farming cooperative ultimately decide to accept the corporate restoration funds?',
     'The text explicitly states that the cooperative accepts funds under binding community control and public scrutiny, structured as a provisional compact that binds authority to disclosure and possible reversal.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0006-000000000002', '30000000-0000-0000-0000-000000000048', 2, 'INFERENCE',
     'What critical dynamic does the narrative expose regarding how institutional procedures utilize technical vocabulary?',
     'The passage observes that efforts to neutralize ambiguity displace it into technical vocabulary where political choices masquerade as inevitable procedure, showing how power dynamics are masked by managerial rhetoric.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0006-000000000003', '30000000-0000-0000-0000-000000000048', 3, 'MAIN_IDEA',
     'Which statement best encapsulates the narrative''s conclusion regarding ethical action amid moral compromise?',
     'The narrative culminates in the understanding that durable integrity lies not in claiming moral perfection or pretending to abolish uncertainty, but in acting with provisional humility, transparent scrutiny, and collective accountability.',
     CURRENT_TIMESTAMP);

INSERT INTO reading_comprehension_options (id, question_id, ordinal, content, is_correct)
VALUES
    ('b0000000-0000-0000-0006-000000000101', 'a0000000-0000-0000-0006-000000000001', 1, 'By conceding permanent executive oversight to corporate environmental scientists', false),
    ('b0000000-0000-0000-0006-000000000102', 'a0000000-0000-0000-0006-000000000001', 2, 'Under binding community control, continuous public scrutiny, and provisional terms subject to principled reversal', true),
    ('b0000000-0000-0000-0006-000000000103', 'a0000000-0000-0000-0006-000000000001', 3, 'Through an unconditional grant that seals chemical soil records from municipal inspectors', false),
    ('b0000000-0000-0000-0006-000000000104', 'a0000000-0000-0000-0006-000000000001', 4, 'By delegating all future restorative land allocations to an external regional tribunal', false),

    ('b0000000-0000-0000-0006-000000000201', 'a0000000-0000-0000-0006-000000000002', 1, 'Technical jargon is deployed to disguise contentious political choices as neutral and inevitable procedural steps.', true),
    ('b0000000-0000-0000-0006-000000000202', 'a0000000-0000-0000-0006-000000000002', 2, 'Specialized chemical terms eliminate ideological division by providing universally accepted empirical certainty.', false),
    ('b0000000-0000-0000-0006-000000000203', 'a0000000-0000-0000-0006-000000000002', 3, 'Bureaucratic language facilitates direct democratic participation by making historical archives immediately accessible to non-specialists.', false),
    ('b0000000-0000-0000-0006-000000000204', 'a0000000-0000-0000-0006-000000000002', 4, 'Agrarian communities adopt corporate vocabulary to avoid legal liability during independent soil remediation.', false),

    ('b0000000-0000-0000-0006-000000000301', 'a0000000-0000-0000-0006-000000000003', 1, 'Moral purity requires rejecting all external funding regardless of the ecological urgency of soil degradation.', false),
    ('b0000000-0000-0000-0006-000000000302', 'a0000000-0000-0000-0006-000000000003', 2, 'Responsible agency entails acting decisively without pretending uncertainty has vanished, anchoring authority in ongoing accountability and revisability.', true),
    ('b0000000-0000-0000-0006-000000000303', 'a0000000-0000-0000-0006-000000000003', 3, 'Community consensus is naturally achieved once contradictory historical archives are permanently excluded from public debate.', false),
    ('b0000000-0000-0000-0006-000000000304', 'a0000000-0000-0000-0006-000000000003', 4, 'Corporate funding mechanisms are fundamentally benign once environmental restoration targets have been scientifically certified.', false);