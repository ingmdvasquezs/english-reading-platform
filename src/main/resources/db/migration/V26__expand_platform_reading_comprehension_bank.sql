-- =============================================================================
-- V26: Expand Reading Comprehension Bank for 6 Authorized Platform Readings
-- Levels: A1, A2, B1, B2, C1, C2
-- Rules: 3 new questions per reading (ordinals 4, 5, 6)
--        ordinal 4 = FACTUAL, ordinal 5 = INFERENCE, ordinal 6 = MAIN_IDEA
--        4 options per question, exactly 1 correct option
-- Total: 18 questions, 72 options
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Reading 1: A1 - The Lost Blue Scarf (20000000-0000-0000-0000-000000000001)
-- -----------------------------------------------------------------------------
INSERT INTO reading_comprehension_questions (id, reading_id, ordinal, question_type, prompt, explanation, created_at)
VALUES
    ('a0000000-0000-0000-0001-000000000004', '20000000-0000-0000-0000-000000000001', 4, 'FACTUAL',
     'Where did Nora and Leo find the blue scarf?',
     'The text explicitly states that Leo pointed at a little brown dog sitting near a bench with the blue scarf under its paws.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0001-000000000005', '20000000-0000-0000-0000-000000000001', 5, 'INFERENCE',
     'Why does Nora speak softly to the dog?',
     'Nora calls gently and softly so she does not frighten the unfamiliar dog into running away before she can pick up her scarf.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0001-000000000006', '20000000-0000-0000-0000-000000000001', 6, 'MAIN_IDEA',
     'What does Nora learn at the end of the story?',
     'Nora realizes that holding and watching her special handmade gift carefully prevents losing it again.',
     CURRENT_TIMESTAMP);

INSERT INTO reading_comprehension_options (id, question_id, ordinal, content, is_correct)
VALUES
    ('b0000000-0000-0000-0001-000000000401', 'a0000000-0000-0000-0001-000000000004', 1, 'Under a table in the bakery', false),
    ('b0000000-0000-0000-0001-000000000402', 'a0000000-0000-0000-0001-000000000004', 2, 'Under the paws of a little brown dog', true),
    ('b0000000-0000-0000-0001-000000000403', 'a0000000-0000-0000-0001-000000000004', 3, 'On a bench near the bus stop', false),
    ('b0000000-0000-0000-0001-000000000404', 'a0000000-0000-0000-0001-000000000004', 4, 'Near the door of the flower shop', false),

    ('b0000000-0000-0000-0001-000000000501', 'a0000000-0000-0000-0001-000000000005', 1, 'She does not want to scare the dog away', true),
    ('b0000000-0000-0000-0001-000000000502', 'a0000000-0000-0000-0001-000000000005', 2, 'She does not want Leo to hear her', false),
    ('b0000000-0000-0000-0001-000000000503', 'a0000000-0000-0000-0001-000000000005', 3, 'She wants to wake up the dog''s owner', false),
    ('b0000000-0000-0000-0001-000000000504', 'a0000000-0000-0000-0001-000000000005', 4, 'Her throat hurts from the cold wind', false),

    ('b0000000-0000-0000-0001-000000000601', 'a0000000-0000-0000-0001-000000000006', 1, 'She should leave her scarf at home when it is cold', false),
    ('b0000000-0000-0000-0001-000000000602', 'a0000000-0000-0000-0001-000000000006', 2, 'She needs to be more careful with things that are special to her', true),
    ('b0000000-0000-0000-0001-000000000603', 'a0000000-0000-0000-0001-000000000006', 3, 'She must walk faster when she goes to the bakery', false),
    ('b0000000-0000-0000-0001-000000000604', 'a0000000-0000-0000-0001-000000000006', 4, 'She has to give warm bread to dogs on the street', false);

-- -----------------------------------------------------------------------------
-- Reading 2: A2 - A Quiet Morning by the River (20000000-0000-0000-0000-000000000006)
-- -----------------------------------------------------------------------------
INSERT INTO reading_comprehension_questions (id, reading_id, ordinal, question_type, prompt, explanation, created_at)
VALUES
    ('a0000000-0000-0000-0002-000000000004', '20000000-0000-0000-0000-000000000006', 4, 'FACTUAL',
     'Which bird did Priya and her father see first?',
     'The passage explicitly states that after hearing moving water, a kingfisher flew low across the river surface like a blue light, and her father wrote its name in his notebook.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0002-000000000005', '20000000-0000-0000-0000-000000000006', 5, 'INFERENCE',
     'What do the different things Priya and her father found show about the river area?',
     'Finding several kinds of healthy wild birds alongside plastic cups dropped on the path shows that the river is a natural habitat that coexists with human visitors.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0002-000000000006', '20000000-0000-0000-0000-000000000006', 6, 'MAIN_IDEA',
     'What does this story show about caring for local nature?',
     'The narrative illustrates how community members help their local environment through small, practical efforts like collecting wildlife data and picking up litter.',
     CURRENT_TIMESTAMP);

INSERT INTO reading_comprehension_options (id, question_id, ordinal, content, is_correct)
VALUES
    ('b0000000-0000-0000-0002-000000000401', 'a0000000-0000-0000-0002-000000000004', 1, 'A gray heron in the reeds', false),
    ('b0000000-0000-0000-0002-000000000402', 'a0000000-0000-0000-0002-000000000004', 2, 'A blue kingfisher flying over the water', true),
    ('b0000000-0000-0000-0002-000000000403', 'a0000000-0000-0000-0002-000000000004', 3, 'A wild duck near the bridge', false),
    ('b0000000-0000-0000-0002-000000000404', 'a0000000-0000-0000-0002-000000000004', 4, 'A small sparrow in the willow tree', false),

    ('b0000000-0000-0000-0002-000000000501', 'a0000000-0000-0000-0002-000000000005', 1, 'The river is visited by people, but it is still home to many wild birds', true),
    ('b0000000-0000-0000-0002-000000000502', 'a0000000-0000-0000-0002-000000000005', 2, 'The river is too dirty for wild birds to live near the water', false),
    ('b0000000-0000-0000-0002-000000000503', 'a0000000-0000-0000-0002-000000000005', 3, 'People are not allowed to walk along the river path', false),
    ('b0000000-0000-0000-0002-000000000504', 'a0000000-0000-0000-0002-000000000005', 4, 'Wild birds only come to the river when people leave plastic cups', false),

    ('b0000000-0000-0000-0002-000000000601', 'a0000000-0000-0000-0002-000000000006', 1, 'Only scientists and town officials can protect wild animals', false),
    ('b0000000-0000-0000-0002-000000000602', 'a0000000-0000-0000-0002-000000000006', 2, 'Ordinary people can help local nature through simple actions like watching wildlife and picking up rubbish', true),
    ('b0000000-0000-0000-0002-000000000603', 'a0000000-0000-0000-0002-000000000006', 3, 'People should only visit rivers on sunny days when there is no rain', false),
    ('b0000000-0000-0000-0002-000000000604', 'a0000000-0000-0000-0002-000000000006', 4, 'Rivers stay clean on their own without any help from the community', false);

-- -----------------------------------------------------------------------------
-- Reading 3: B1 - Learning to Ask Better Questions (20000000-0000-0000-0000-000000000011)
-- -----------------------------------------------------------------------------
INSERT INTO reading_comprehension_questions (id, reading_id, ordinal, question_type, prompt, explanation, created_at)
VALUES
    ('a0000000-0000-0000-0003-000000000004', '20000000-0000-0000-0000-000000000011', 4, 'FACTUAL',
     'What did Nadia ask customers when they reported an error?',
     'The text explicitly lists the questions Nadia asked: when the error appeared, what had changed, and whether other people had experienced it.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0003-000000000005', '20000000-0000-0000-0000-000000000011', 5, 'INFERENCE',
     'If Nadia faced a new problem that she had never seen before at work, what would she most likely do first?',
     'Based on her experience at the bicycle workshop and in customer support, Nadia learned to examine components, ask questions, and identify what changed rather than guessing impulsively or rushing to replace parts.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0003-000000000006', '20000000-0000-0000-0000-000000000011', 6, 'MAIN_IDEA',
     'What main message does Nadia''s experience show about learning new skills?',
     'The story demonstrates how a diagnostic mindset learned in a bicycle workshop transfers across different domains, improving customer support, documentation, and peer mentorship.',
     CURRENT_TIMESTAMP);

INSERT INTO reading_comprehension_options (id, question_id, ordinal, content, is_correct)
VALUES
    ('b0000000-0000-0000-0003-000000000401', 'a0000000-0000-0000-0003-000000000004', 1, 'How much they had paid and where they had bought the product', false),
    ('b0000000-0000-0000-0003-000000000402', 'a0000000-0000-0000-0003-000000000004', 2, 'When the problem started, what had changed, and whether other people had the same problem', true),
    ('b0000000-0000-0000-0003-000000000403', 'a0000000-0000-0000-0003-000000000004', 3, 'What kind of computer they used and how often they updated the software', false),
    ('b0000000-0000-0000-0003-000000000404', 'a0000000-0000-0000-0003-000000000004', 4, 'Whether they had already complained to a manager about the issue', false),

    ('b0000000-0000-0000-0003-000000000501', 'a0000000-0000-0000-0003-000000000005', 1, 'Suggest replacing the entire system immediately without checking its parts', false),
    ('b0000000-0000-0000-0003-000000000502', 'a0000000-0000-0000-0003-000000000005', 2, 'Ask questions to find out what changed and understand the situation before choosing a fix', true),
    ('b0000000-0000-0000-0003-000000000503', 'a0000000-0000-0000-0003-000000000005', 3, 'Guess a quick answer right away so she does not look inexperienced', false),
    ('b0000000-0000-0000-0003-000000000504', 'a0000000-0000-0000-0003-000000000005', 4, 'Wait for a senior manager to take over the task completely', false),

    ('b0000000-0000-0000-0003-000000000601', 'a0000000-0000-0000-0003-000000000006', 1, 'Learning a practical method in one activity can help people solve problems and work better in other areas of life', true),
    ('b0000000-0000-0000-0003-000000000602', 'a0000000-0000-0000-0003-000000000006', 2, 'Employees should focus on office computer work rather than manual mechanical repairs', false),
    ('b0000000-0000-0000-0003-000000000603', 'a0000000-0000-0000-0003-000000000006', 3, 'Workplace problems can always be solved faster by replacing damaged parts immediately', false),
    ('b0000000-0000-0000-0003-000000000604', 'a0000000-0000-0000-0003-000000000006', 4, 'Customer service teams should avoid writing down their past repair attempts', false);

-- -----------------------------------------------------------------------------
-- Reading 4: B2 - The Cost of Constant Attention (20000000-0000-0000-0000-000000000013)
-- -----------------------------------------------------------------------------
INSERT INTO reading_comprehension_questions (id, reading_id, ordinal, question_type, prompt, explanation, created_at)
VALUES
    ('a0000000-0000-0000-0004-000000000004', '20000000-0000-0000-0000-000000000013', 4, 'FACTUAL',
     'What unexpected change did Inez observe in her everyday routines outside of the workplace?',
     'The passage explicitly states: "Conversations felt less hurried, and waiting in a queue became an occasion to observe rather than consume."',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0004-000000000005', '20000000-0000-0000-0000-000000000013', 5, 'INFERENCE',
     'Why did reducing notifications feel uncomfortable for Inez during the first few days?',
     'Because Inez was conditioned to check her phone constantly, the sudden silence made her feel anxious that important updates were passing her by.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0004-000000000006', '20000000-0000-0000-0000-000000000013', 6, 'MAIN_IDEA',
     'What main point does the passage make about managing digital distractions?',
     'The passage concludes that regaining focus requires individual discipline and personal boundaries, while recognizing that digital platforms are engineered to capture user attention rather than maximize user value.',
     CURRENT_TIMESTAMP);

INSERT INTO reading_comprehension_options (id, question_id, ordinal, content, is_correct)
VALUES
    ('b0000000-0000-0000-0004-000000000401', 'a0000000-0000-0000-0004-000000000004', 1, 'She lost all interest in following global journalism and social debates', false),
    ('b0000000-0000-0000-0004-000000000402', 'a0000000-0000-0000-0004-000000000004', 2, 'Conversations felt less hurried and waiting in lines became an opportunity to observe', true),
    ('b0000000-0000-0000-0004-000000000403', 'a0000000-0000-0000-0004-000000000004', 3, 'She began avoiding public spaces that offered wireless internet connectivity', false),
    ('b0000000-0000-0000-0004-000000000404', 'a0000000-0000-0000-0004-000000000004', 4, 'Her coworkers refused to communicate with her outside of scheduled office hours', false),

    ('b0000000-0000-0000-0004-000000000501', 'a0000000-0000-0000-0004-000000000005', 1, 'Her smartphone stopped receiving urgent company emails completely', false),
    ('b0000000-0000-0000-0004-000000000502', 'a0000000-0000-0000-0004-000000000005', 2, 'She was used to constant digital alerts and felt anxious that she was missing information', true),
    ('b0000000-0000-0000-0004-000000000503', 'a0000000-0000-0000-0004-000000000005', 3, 'Her manager warned her that she was taking too long to answer casual messages', false),
    ('b0000000-0000-0000-0004-000000000504', 'a0000000-0000-0000-0004-000000000005', 4, 'Her friends stopped inviting her to social events because she turned off her phone', false),

    ('b0000000-0000-0000-0004-000000000601', 'a0000000-0000-0000-0004-000000000006', 1, 'Individuals must set deliberate boundaries because digital services are designed to compete for as much user time as possible', true),
    ('b0000000-0000-0000-0004-000000000602', 'a0000000-0000-0000-0004-000000000006', 2, 'Companies should completely ban mobile phones from the workplace to protect employee concentration', false),
    ('b0000000-0000-0000-0004-000000000603', 'a0000000-0000-0000-0004-000000000006', 3, 'Personal willpower alone is always enough to overcome any digital distraction without changing app settings', false),
    ('b0000000-0000-0000-0004-000000000604', 'a0000000-0000-0000-0004-000000000006', 4, 'Digital technology offers no real opportunities for human connection or workplace communication', false);

-- -----------------------------------------------------------------------------
-- Reading 5: C1 - The Museum of Unfinished Things (20000000-0000-0000-0000-000000000017)
-- -----------------------------------------------------------------------------
INSERT INTO reading_comprehension_questions (id, reading_id, ordinal, question_type, prompt, explanation, created_at)
VALUES
    ('a0000000-0000-0000-0005-000000000004', '20000000-0000-0000-0000-000000000017', 4, 'FACTUAL',
     'According to the exhibition notebooks, what secondary technological development arose from the abandoned medical device?',
     'The text explicitly states: "A medical device had become obsolete before approval, although its sensor later improved farming equipment."',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0005-000000000005', '20000000-0000-0000-0000-000000000017', 5, 'INFERENCE',
     'Why was displaying detailed notebooks beside each object essential to achieving Dr. Vale''s curatorial vision?',
     'The objects in isolation appeared only as humorous failures to visitors; only the notebooks provided the record of compromises, constraints, and decisions necessary to understand them as instructive attempts.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0005-000000000006', '20000000-0000-0000-0000-000000000017', 6, 'MAIN_IDEA',
     'Which overarching argument about human progress does the text as a whole advance?',
     'The passage argues across all its episodes that incomplete endeavors illuminate hidden decisions, unintended breakthroughs, and structural inequalities that polished success stories obscure.',
     CURRENT_TIMESTAMP);

INSERT INTO reading_comprehension_options (id, question_id, ordinal, content, is_correct)
VALUES
    ('b0000000-0000-0000-0005-000000000401', 'a0000000-0000-0000-0005-000000000004', 1, 'Its hydraulic chassis was adapted to stabilize high-speed railway freight cars', false),
    ('b0000000-0000-0000-0005-000000000402', 'a0000000-0000-0000-0005-000000000004', 2, 'Its sensor mechanism was subsequently used to improve farming equipment', true),
    ('b0000000-0000-0000-0005-000000000403', 'a0000000-0000-0000-0005-000000000004', 3, 'Its structural blueprints inspired municipal pedestrian bridge designs abroad', false),
    ('b0000000-0000-0000-0005-000000000404', 'a0000000-0000-0000-0005-000000000004', 4, 'Its timber frame established an innovative method for steam-bending domestic furniture', false),

    ('b0000000-0000-0000-0005-000000000501', 'a0000000-0000-0000-0005-000000000005', 1, 'To verify the legal patents and formal ownership rights of the original inventors', false),
    ('b0000000-0000-0000-0005-000000000502', 'a0000000-0000-0000-0005-000000000005', 2, 'Because without documentation of compromises and constraints, visitors would merely view the artifacts as amusing technical blunders rather than serious design processes', true),
    ('b0000000-0000-0000-0005-000000000503', 'a0000000-0000-0000-0005-000000000005', 3, 'To provide step-by-step technical instructions so that visitors could complete the unfinished devices at home', false),
    ('b0000000-0000-0000-0005-000000000504', 'a0000000-0000-0000-0005-000000000005', 4, 'To prove that formal university credentials are required to design complex mechanical prototypes', false),

    ('b0000000-0000-0000-0005-000000000601', 'a0000000-0000-0000-0005-000000000006', 1, 'Technological innovation thrives best when creators are completely insulated from public scrutiny and community input', false),
    ('b0000000-0000-0000-0005-000000000602', 'a0000000-0000-0000-0005-000000000006', 2, 'Human endeavor cannot be judged solely by immediate functional success, as incomplete work reveals the complex trade-offs, social barriers, and unexpected insights that shape knowledge', true),
    ('b0000000-0000-0000-0005-000000000603', 'a0000000-0000-0000-0005-000000000006', 3, 'Institutional archives are inherently more reliable than community memories because they preserve only commercially viable inventions', false),
    ('b0000000-0000-0000-0005-000000000604', 'a0000000-0000-0000-0005-000000000006', 4, 'Every abandoned project represents an unmitigated waste of material resources that society should actively suppress', false);

-- -----------------------------------------------------------------------------
-- Reading 6: C2 - The Inheritance of Dust (30000000-0000-0000-0000-000000000048)
-- -----------------------------------------------------------------------------
INSERT INTO reading_comprehension_questions (id, reading_id, ordinal, question_type, prompt, explanation, created_at)
VALUES
    ('a0000000-0000-0000-0006-000000000004', '30000000-0000-0000-0000-000000000048', 4, 'FACTUAL',
     'What administrative mechanism do Amina, Bastien, and the cooperative establish to sustain community oversight once initial enthusiasm subsides?',
     'The passage explicitly states: "Later, farmer Amina points out that useful change depends on habits after the exciting moment has passed... They write a simple plan, assign responsibility, and agree on a date when the result will be reviewed."',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0006-000000000005', '30000000-0000-0000-0000-000000000048', 5, 'INFERENCE',
     'Why does Bastien insist on deliberately pausing, inviting external criticism, and testing findings against skeptical observers before formalizing a decision?',
     'Because different parties operated within distinct, internally coherent moral horizons and procedures can manufacture artificial consent, Bastien deliberately invited skepticism to prevent premature, self-serving consensus.',
     CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0006-000000000006', '30000000-0000-0000-0000-000000000048', 6, 'MAIN_IDEA',
     'Which fundamental tension concerning democratic accountability and institutional power forms the primary axis of the narrative?',
     'The narrative explores how institutional processes can simulate consensus, concluding that legitimate governance demands provisional authority, transparency, and the courage to act responsibly amidst irreducible uncertainty.',
     CURRENT_TIMESTAMP);

INSERT INTO reading_comprehension_options (id, question_id, ordinal, content, is_correct)
VALUES
    ('b0000000-0000-0000-0006-000000000401', 'a0000000-0000-0000-0006-000000000004', 1, 'They execute an irrevocable contract entrusting long-term monitoring to corporate agronomists', false),
    ('b0000000-0000-0000-0006-000000000402', 'a0000000-0000-0000-0006-000000000004', 2, 'They formulate a structured plan, delegate explicit responsibilities, and set a mandatory review date', true),
    ('b0000000-0000-0000-0006-000000000403', 'a0000000-0000-0000-0006-000000000004', 3, 'They establish a closed regional tribunal to arbitrate chemical safety disputes in private', false),
    ('b0000000-0000-0000-0006-000000000404', 'a0000000-0000-0000-0006-000000000004', 4, 'They require daily soil testing conducted exclusively by state regulatory inspectors', false),

    ('b0000000-0000-0000-0006-000000000501', 'a0000000-0000-0000-0006-000000000005', 1, 'He suspects that the chemical histories provided to the cooperative were completely fabricated by municipal authorities', false),
    ('b0000000-0000-0000-0006-000000000502', 'a0000000-0000-0000-0006-000000000005', 2, 'He recognizes that when confronting incompatible moral frameworks, authentic legitimacy requires systematically exposing assumptions to dissenting cross-examination', true),
    ('b0000000-0000-0000-0006-000000000503', 'a0000000-0000-0000-0006-000000000005', 3, 'He seeks to stall community negotiations indefinitely until the corporate benefactor withdraws its remediation capital', false),
    ('b0000000-0000-0000-0006-000000000504', 'a0000000-0000-0000-0006-000000000005', 4, 'He believes rural cooperatives are fundamentally unequipped to interpret agricultural data without external academic mediation', false),

    ('b0000000-0000-0000-0006-000000000601', 'a0000000-0000-0000-0006-000000000006', 1, 'The inevitable rejection of modern scientific soil analysis by traditional agrarian communities', false),
    ('b0000000-0000-0000-0006-000000000602', 'a0000000-0000-0000-0006-000000000006', 2, 'The tendency of procedural systems to manufacture artificial legitimacy, requiring communities to bind authority to continuous transparency, contestability, and provisional action', true),
    ('b0000000-0000-0000-0006-000000000603', 'a0000000-0000-0000-0006-000000000006', 3, 'The presumption that corporate capital is inherently benign whenever invested in environmental ecological projects', false),
    ('b0000000-0000-0000-0006-000000000604', 'a0000000-0000-0000-0006-000000000006', 4, 'The conviction that absolute certainty must be fully established before any community can ethically engage in collective remediation', false);