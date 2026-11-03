// by J16D
// sp_ai - Spider-Man AI (ambient encounters)
// Spider-Man Mod for GTA SA c.2018 - 2021
//
// THIS IS THE "AI" OF THE MOD. It is controlled by the [ai] section of
// CLEO\SpiderJ16D\config.ini:
//
//   mode = 0  -> AI disabled (normal mod, player is the only Spider-Man)
//   mode = 1  -> player + AI (the AI may be found around the map)
//   mode = 2  -> AI only (the player has NOTHING from the mod: no menu,
//                no buttons, no messages, no hud. Only the AI exists)
//
// The AI spawns off screen, swings to the player area and then does a
// random sequence of actions (patrol / street crime / car chase).
// Every finished action goes to the next one and, at the end, the AI
// leaves the area and the script goes back to "waiting for the next
// encounter" (cheap idle loop).
//
// You need CLEO+: https://forum.mixmods.com.br/f141-gta3script-cleo/t5206-como-criar-scripts-com-cleo

//-+---CONSTANTS--------------------
//GLOBAL_CLEO_SHARED_VARS
CONST_INT varStatusSpiderMod    0     //1= Mod activated || 0= Mod Deactivated
CONST_INT varOnmission          11    //0:Off || 1:on mission ...
CONST_INT varInMenu             40    //1= On Menu     || 0= Menu Closed
CONST_INT varAiRunning          59    //free slot: 1= sp_ai thread already running

//AI MODES
CONST_INT AI_MODE_NORMAL        0     //normal mod (no AI)
CONST_INT AI_MODE_PLAYER        1     //player + IA
CONST_INT AI_MODE_ONLY          2     //IA only

//ACTIONS
CONST_INT ACT_ARRIVE            0
CONST_INT ACT_PATROL            1
CONST_INT ACT_CRIME             2
CONST_INT ACT_CHASE             3
CONST_INT ACT_LEAVE             4

//TUNING
CONST_INT ai_special_slot       10    //SPECIAL10 (8=player suit, 9=missions)
CONST_INT ai_actions_encounter  3     //actions before leaving
CONST_INT ai_thug_health        170
CONST_INT ai_web_damage         25
CONST_INT ai_max_leap_ticks     14
CONST_INT ai_roll_min           45000 //ms - minimum time between two rolls
CONST_INT ai_use_spider_anim    1     //1= AI uses the "spider" anim group (set 0 if T-pose)
CONST_INT ai_leave_cooldown     60000 //ms - rest after the AI leaves

CONST_FLOAT ai_spawn_back       -85.0
CONST_FLOAT ai_despawn_distance 260.0
CONST_FLOAT ai_max_height_diff  20.0
CONST_FLOAT ai_arc_height       10.0

SCRIPT_START
{
SCRIPT_NAME sp_ai
WAIT 0
WAIT 0
WAIT 0
WAIT 0
WAIT 0
LVAR_INT player_actor mode toggleSpiderMod flag_player_on_mission isInMainMenu iChance iCooldown
LVAR_INT ai_actor ai_blip actionId actionsDone iTemp iRand iSuit iLang
LVAR_INT iTarget iTarget2 iTarget3 iCar iDriver iCount iTick
LVAR_FLOAT fX fY fZ fAX fAY fAZ fD fP fA fG

GET_PLAYER_CHAR 0 player_actor

//-+--- only one instance of the AI
GET_CLEO_SHARED_VAR varAiRunning (iTemp)
IF iTemp = 1
    TERMINATE_THIS_CUSTOM_SCRIPT
ENDIF
SET_CLEO_SHARED_VAR varAiRunning 1

GOSUB read_config
IF mode = AI_MODE_NORMAL
    SET_CLEO_SHARED_VAR varAiRunning 0
    WAIT 50
    TERMINATE_THIS_CUSTOM_SCRIPT
ENDIF

ai_actor = 0
ai_blip = 0
actionId = ACT_ARRIVE
actionsDone = 0
iTarget = 0
iTarget2 = 0
iTarget3 = 0
iCar = 0
iDriver = 0

//-+--- MAIN LOOP (cheap while nobody found the AI)
idle_loop:
    WAIT 1000
    GOSUB read_vars
    GOSUB ai_can_encounter
    IF iTemp = 0
        GENERATE_RANDOM_INT_IN_RANGE 0 100 (iRand)
        IF iRand < iChance
            GOSUB spawn_ai
            IF ai_actor > 0
                GOSUB encounter
                WAIT ai_leave_cooldown
            ELSE
                WAIT iCooldown
            ENDIF
        ELSE
            WAIT iCooldown
        ENDIF
    ENDIF
GOTO idle_loop

//-+-----------------------------------------------------------------
//-+ CONFIG
//-+-----------------------------------------------------------------
read_config:
    mode = AI_MODE_PLAYER
    iChance = 35
    iCooldown = ai_roll_min
    iSuit = 2
    iLang = 1   //0:spanish || 1:English
    IF DOES_FILE_EXIST "CLEO\SpiderJ16D\config.ini"
        iTemp = -1
        READ_INT_FROM_INI_FILE "CLEO\SpiderJ16D\config.ini" "ai" "mode" (iTemp)
        IF iTemp >= 0
        AND 2 >= iTemp
            mode = iTemp
        ENDIF
        iTemp = -1
        READ_INT_FROM_INI_FILE "CLEO\SpiderJ16D\config.ini" "ai" "chance" (iTemp)
        IF iTemp >= 0
            iChance = iTemp
        ENDIF
        iTemp = 0
        READ_INT_FROM_INI_FILE "CLEO\SpiderJ16D\config.ini" "ai" "interval" (iTemp)
        IF iTemp >= 5000
            iCooldown = iTemp
        ENDIF
        iTemp = -1
        READ_INT_FROM_INI_FILE "CLEO\SpiderJ16D\config.ini" "ai" "suit" (iTemp)
        IF iTemp > 0
            iSuit = iTemp
        ENDIF
        iTemp = -1
        READ_INT_FROM_INI_FILE "CLEO\SpiderJ16D\config.ini" "config" "LANG" (iTemp)
        IF iTemp = 0
        OR iTemp = 1
            iLang = iTemp
        ENDIF
    ENDIF
    IF iChance < 0
        iChance = 0
    ENDIF
    IF iChance > 100
        iChance = 100
    ENDIF
    IF iCooldown < 5000
        iCooldown = 5000
    ENDIF
RETURN

read_vars:
    GET_PLAYER_CHAR 0 player_actor
    GET_CLEO_SHARED_VAR varStatusSpiderMod (toggleSpiderMod)
    GET_CLEO_SHARED_VAR varOnmission (flag_player_on_mission)
    GET_CLEO_SHARED_VAR varInMenu (isInMainMenu)
RETURN

//-+--- can the AI be found right now?  -> iTemp: 0 = yes | 1 = no
//NOTE: do not use RETURN_TRUE/RETURN_FALSE here. gta3sc compiles them to
//0x0485 / 0x059A for SA, which do NOT return from the subroutine (the
//routine falls through to the next one, keeping the caller logic broken).
//So the result goes in iTemp and the routine leaves with a plain RETURN.
ai_can_encounter:
    iTemp = 1
    IF NOT IS_PLAYER_PLAYING 0
        RETURN
    ENDIF
    IF mode = AI_MODE_PLAYER
        IF toggleSpiderMod = 0
            RETURN
        ENDIF
    ENDIF
    IF flag_player_on_mission > 0
        RETURN
    ENDIF
    IF isInMainMenu = 1
        RETURN
    ENDIF
    IF IS_CHAR_DEAD player_actor
        RETURN
    ENDIF
    IF IS_CHAR_IN_ANY_CAR player_actor
        RETURN
    ENDIF
    IF IS_ON_MISSION
        RETURN
    ENDIF
    IF IS_ON_SCRIPTED_CUTSCENE
        RETURN
    ENDIF
    IF IS_ON_CUTSCENE
        RETURN
    ENDIF
    GET_AREA_VISIBLE (iTemp)
    IF iTemp > 0    //interior
        iTemp = 1
        RETURN
    ENDIF
    iTemp = 0
RETURN

//-+--- abort a running encounter (player died, mission started, mod off...)
ai_shouldabort:
    iTemp = 1
    IF NOT IS_PLAYER_PLAYING 0
        RETURN
    ENDIF
    GOSUB read_vars
    IF mode = AI_MODE_PLAYER
        IF toggleSpiderMod = 0
            RETURN
        ENDIF
    ENDIF
    IF flag_player_on_mission > 0
        RETURN
    ENDIF
    IF isInMainMenu = 1
        RETURN
    ENDIF
    IF IS_CHAR_DEAD player_actor
        RETURN
    ENDIF
    IF DOES_CHAR_EXIST ai_actor
        IF IS_CHAR_DEAD ai_actor
            RETURN
        ENDIF
        IF NOT LOCATE_CHAR_DISTANCE_TO_CHAR ai_actor player_actor ai_despawn_distance
            RETURN
        ENDIF
        iTemp = 0
    ENDIF
RETURN

//-+-----------------------------------------------------------------
//-+ SPAWN
//-+-----------------------------------------------------------------
get_spawn_coords:
    //returns iTemp: 1= ok (fX fY fZ) | 0= fail
    iTemp = 0
    GENERATE_RANDOM_FLOAT_IN_RANGE -25.0 25.0 (fA)
    //point behind the player (that is why he is not seen arriving)
    GET_OFFSET_FROM_CHAR_IN_WORLD_COORDS player_actor fA ai_spawn_back 0.0 (fX fY fZ)
    GET_GROUND_Z_FOR_3D_COORD fX fY fZ (fG)
    IF fG < -1.0
        RETURN
    ENDIF
    fA = fZ
    fA -= fG
    IF fA > ai_max_height_diff
        RETURN
    ENDIF
    fA = fG
    fA -= fZ
    IF fA > ai_max_height_diff
        RETURN
    ENDIF
    IF IS_POINT_ON_SCREEN fX fY fZ 6.0
        RETURN
    ENDIF
    GET_WATER_HEIGHT_AT_COORDS fX fY TRUE (fP)
    IF fP > fG   //do not spawn over water
        RETURN
    ENDIF
    fZ = fG + 0.8
    iTemp = 1
RETURN

spawn_ai:
    ai_actor = 0
    GOSUB set_suit_model
    GOSUB load_assets
    iTick = 0
    WHILE iTick < 4
        iTick ++
        GOSUB get_spawn_coords
        IF iTemp = 1
            BREAK
        ENDIF
        WAIT 100
    ENDWHILE
    IF iTemp = 0
        RETURN
    ENDIF
    CREATE_CHAR PEDTYPE_CIVMALE SPECIAL10 fX fY fZ (ai_actor)
    IF NOT DOES_CHAR_EXIST ai_actor
        ai_actor = 0
        RETURN
    ENDIF
    SET_CHAR_MAX_HEALTH ai_actor 600
    SET_CHAR_HEALTH ai_actor 600
    SET_CHAR_PROOFS ai_actor FALSE FALSE FALSE FALSE FALSE
    SET_CHAR_DROPS_WEAPONS_WHEN_DEAD ai_actor FALSE
    SET_CHAR_NEVER_TARGETTED ai_actor FALSE
    SET_CHAR_ACCURACY ai_actor 100
    FIX_CHAR_GROUND_BRIGHTNESS_AND_FADE_IN ai_actor TRUE TRUE TRUE
    iTemp = ai_use_spider_anim
    IF iTemp = 1
        SET_ANIM_GROUP_FOR_CHAR ai_actor spider
    ENDIF
    SET_CHAR_COLLISION ai_actor FALSE   //he arrives by "web swing" (turned on when he lands)
    //faces the player
    GET_CHAR_HEADING player_actor (fA)
    fA += 180.0
    SET_CHAR_HEADING ai_actor fA
    IF mode = AI_MODE_PLAYER
        ADD_BLIP_FOR_CHAR ai_actor (ai_blip)
        CHANGE_BLIP_DISPLAY ai_blip BLIP_ONLY
        SET_BLIP_ALWAYS_DISPLAY_ON_ZOOMED_RADAR ai_blip TRUE
    ENDIF
    actionId = ACT_ARRIVE
    actionsDone = 0
    timera = 0
RETURN

//-+--- suit models (slot 10 of the special characters)
set_suit_model:
    SWITCH iSuit
        CASE 1
            LOAD_SPECIAL_CHARACTER ai_special_slot s1
            BREAK
        CASE 2
            LOAD_SPECIAL_CHARACTER ai_special_slot s2
            BREAK
        CASE 3
            LOAD_SPECIAL_CHARACTER ai_special_slot s3
            BREAK
        CASE 4
            LOAD_SPECIAL_CHARACTER ai_special_slot s4
            BREAK
        CASE 5
            LOAD_SPECIAL_CHARACTER ai_special_slot s5
            BREAK
        CASE 6
            LOAD_SPECIAL_CHARACTER ai_special_slot s6
            BREAK
        CASE 7
            LOAD_SPECIAL_CHARACTER ai_special_slot s7
            BREAK
        CASE 8
            LOAD_SPECIAL_CHARACTER ai_special_slot s8
            BREAK
        CASE 9
            LOAD_SPECIAL_CHARACTER ai_special_slot s9
            BREAK
        DEFAULT
            LOAD_SPECIAL_CHARACTER ai_special_slot s2
            BREAK
    ENDSWITCH
    LOAD_ALL_MODELS_NOW
RETURN

load_assets:
    REQUEST_MODEL BALLAS1
    REQUEST_MODEL BALLAS2
    REQUEST_MODEL VMAFF1
    REQUEST_MODEL SENTINEL
    REQUEST_MODEL KNIFECUR
    REQUEST_MODEL BAT
    REQUEST_ANIMATION "spider"
    LOAD_ALL_MODELS_NOW
RETURN

unload_assets:
    MARK_MODEL_AS_NO_LONGER_NEEDED BALLAS1
    MARK_MODEL_AS_NO_LONGER_NEEDED BALLAS2
    MARK_MODEL_AS_NO_LONGER_NEEDED VMAFF1
    MARK_MODEL_AS_NO_LONGER_NEEDED SENTINEL
    MARK_MODEL_AS_NO_LONGER_NEEDED KNIFECUR
    MARK_MODEL_AS_NO_LONGER_NEEDED BAT
RETURN

//-+-----------------------------------------------------------------
//-+ ENCOUNTER / ACTION SEQUENCE
//-+-----------------------------------------------------------------
encounter:
    actionId = ACT_ARRIVE
    actionsDone = 0
    WHILE TRUE
        SWITCH actionId
            CASE ACT_ARRIVE
                GOSUB act_arrive
                BREAK
            CASE ACT_PATROL
                GOSUB act_patrol
                BREAK
            CASE ACT_CRIME
                GOSUB act_crime
                BREAK
            CASE ACT_CHASE
                GOSUB act_chase
                BREAK
            DEFAULT
                actionId = ACT_LEAVE
                BREAK
        ENDSWITCH
        GOSUB ai_shouldabort
        IF iTemp = 1
            GOSUB cleanup
            RETURN
        ENDIF
        actionsDone ++
        IF actionsDone >= ai_actions_encounter
            actionId = ACT_LEAVE
        ELSE
            GENERATE_RANDOM_INT_IN_RANGE 1 4 (actionId)  //1..3: patrol / crime / chase
        ENDIF
        IF actionId = ACT_LEAVE
            GOSUB act_leave
            BREAK
        ENDIF
    ENDWHILE
    GOSUB cleanup
    RETURN

//-+--- the AI arrives swinging, lands near the player and greets
act_arrive:
    iRand = 1
    GOSUB ai_say
    GET_OFFSET_FROM_CHAR_IN_WORLD_COORDS player_actor 0.0 10.0 0.0 (fX fY fZ)
    GET_GROUND_Z_FOR_3D_COORD fX fY fZ (fG)
    IF fG > -1.0
        fZ = fG + 0.5
    ENDIF
    GOSUB ai_travel
    IF NOT DOES_CHAR_EXIST ai_actor
        RETURN
    ENDIF
    TASK_TURN_CHAR_TO_FACE_COORD ai_actor fX fY fZ
    TASK_PLAY_ANIM_NON_INTERRUPTABLE ai_actor ("menu_idle_A" "spider") 30.0 (0 1 1 0) 2500
    WAIT 1200
RETURN

//-+--- swings around the player area (the player may see him on the roofs)
act_patrol:
    iTick = 0
    WHILE iTick < 5
        iTick ++
        GOSUB ai_shouldabort
        IF iTemp = 1
            RETURN
        ENDIF
        GENERATE_RANDOM_FLOAT_IN_RANGE -50.0 50.0 (fA)
        GENERATE_RANDOM_FLOAT_IN_RANGE 25.0 70.0 (fD)
        GET_OFFSET_FROM_CHAR_IN_WORLD_COORDS player_actor fA fD 0.0 (fX fY fZ)
        GET_GROUND_Z_FOR_3D_COORD fX fY fZ (fG)
        IF fG > -1.0
            fZ = fG + 0.5
            GOSUB ai_travel
            WAIT 900
        ENDIF
        IF iTick = 3
            IF DOES_CHAR_EXIST ai_actor
                TASK_PLAY_ANIM_NON_INTERRUPTABLE ai_actor ("t_tower_A" "spider") 20.0 (0 1 1 0) 2000
                WAIT 1200
            ENDIF
        ENDIF
    ENDWHILE
RETURN

//-+--- street crime: thugs attacking a pedestrian
act_crime:
    GENERATE_RANDOM_FLOAT_IN_RANGE -25.0 25.0 (fA)
    GENERATE_RANDOM_FLOAT_IN_RANGE 15.0 30.0 (fD)
    GET_OFFSET_FROM_CHAR_IN_WORLD_COORDS player_actor fA fD 0.0 (fX fY fZ)
    GET_GROUND_Z_FOR_3D_COORD fX fY fZ (fG)
    IF fG < -1.0
        RETURN
    ENDIF
    fZ = fG + 0.5
    GOSUB ai_travel
    GOSUB ai_shouldabort
    IF iTemp = 1
        RETURN
    ENDIF
    GOSUB ai_spawn_thugs
    iRand = 2
    GOSUB ai_say
    timera = 0
    WHILE TRUE
        GOSUB ai_shouldabort
        IF iTemp = 1
            GOSUB ai_clear_thugs
            RETURN
        ENDIF
        GOSUB ai_fight_tick
        IF iCount = 0
            GOSUB ai_clear_thugs
            iRand = 3
            GOSUB ai_say
            GOSUB update_stat_crime
            RETURN
        ENDIF
        IF timera > 90000
            GOSUB ai_clear_thugs
            RETURN
        ENDIF
        WAIT 600
    ENDWHILE
RETURN

//-+--- car chase: the AI webs the getaway car and stops the driver
act_chase:
    GENERATE_RANDOM_FLOAT_IN_RANGE -25.0 25.0 (fA)
    GET_OFFSET_FROM_CHAR_IN_WORLD_COORDS player_actor fA 55.0 0.0 (fX fY fZ)
    GET_CLOSEST_CAR_NODE fX fY fZ (fAX fAY fAZ)
    fX = fAX
    fY = fAY
    fZ = fAZ
    GET_GROUND_Z_FOR_3D_COORD fX fY fZ (fG)
    IF fG < -1.0
        RETURN
    ENDIF
    fZ = fG + 0.5
    CREATE_CAR SENTINEL fX fY fZ (iCar)
    IF NOT DOES_VEHICLE_EXIST iCar
        iCar = 0
        RETURN
    ENDIF
    SET_CAR_HEALTH iCar 1000
    CREATE_CHAR_INSIDE_CAR iCar PEDTYPE_MISSION1 VMAFF1 (iDriver)
    IF DOES_CHAR_EXIST iDriver
        SET_CHAR_HEALTH iDriver ai_thug_health
        GIVE_WEAPON_TO_CHAR iDriver WEAPONTYPE_KNIFE 1
        SET_CURRENT_CHAR_WEAPON iDriver WEAPONTYPE_KNIFE
        SET_CHAR_DROPS_WEAPONS_WHEN_DEAD iDriver TRUE
        GOSUB ai_drive_away
    ENDIF
    iRand = 4
    GOSUB ai_say
    iTick = 0
    timera = 0
    //pursuit
    WHILE TRUE
        GOSUB ai_shouldabort
        IF iTemp = 1
            GOSUB ai_clear_chase
            RETURN
        ENDIF
        IF NOT DOES_VEHICLE_EXIST iCar
            GOSUB ai_clear_chase
            RETURN
        ENDIF
        IF IS_CAR_DEAD iCar
            BREAK
        ENDIF
        GET_CAR_COORDINATES iCar (fX fY fZ)
        GET_CHAR_COORDINATES ai_actor (fAX fAY fAZ)
        GET_DISTANCE_BETWEEN_COORDS_3D fAX fAY fAZ fX fY fZ (fD)
        GET_GROUND_Z_FOR_3D_COORD fX fY fZ (fG)
        IF fG < -1.0
            fG = fZ
        ENDIF
        IF fD > 14.0
            GOSUB ai_travel
        ELSE
            GOSUB ai_web_car
            IF iTick = 1
                BREAK
            ENDIF
        ENDIF
        IF timera > 75000
            GOSUB ai_clear_chase
            RETURN
        ENDIF
        WAIT 250
    ENDWHILE
    //the car is webbed: the driver leaves and fights on foot
    IF DOES_CHAR_EXIST iDriver
        TASK_LEAVE_ANY_CAR iDriver
        WAIT 2000
        iTarget = iDriver
        iTarget2 = 0
        iTarget3 = 0
        timera = 0
        WHILE TRUE
            GOSUB ai_shouldabort
            IF iTemp = 1
                BREAK
            ENDIF
            GOSUB ai_fight_tick
            IF iCount = 0
                BREAK
            ENDIF
            IF timera > 45000
                BREAK
            ENDIF
            WAIT 600
        ENDWHILE
    ENDIF
    GOSUB ai_clear_chase
    iRand = 5
    GOSUB ai_say
    GOSUB update_stat_chase
RETURN

//-+--- leaves: swings far away (then the cleanup removes him off screen)
act_leave:
    iRand = 6
    GOSUB ai_say
    iTick = 0
    WHILE iTick < 3
        iTick ++
        IF NOT DOES_CHAR_EXIST ai_actor
            RETURN
        ENDIF
        GENERATE_RANDOM_FLOAT_IN_RANGE -25.0 25.0 (fA)
        GET_OFFSET_FROM_CHAR_IN_WORLD_COORDS ai_actor fA -45.0 0.0 (fX fY fZ)
        GET_GROUND_Z_FOR_3D_COORD fX fY fZ (fG)
        IF fG > -1.0
            fZ = fG + 0.5
            GOSUB ai_leap_to
        ENDIF
    ENDWHILE
RETURN

//-+-----------------------------------------------------------------
//-+ MOVEMENT (scripted web swing)
//-+-----------------------------------------------------------------
//destination in fX fY fZ, ground z in fG
ai_travel:
    iCount = 0
    WHILE iCount < 6
        iCount ++
        GOSUB ai_shouldabort
        IF iTemp = 1
            RETURN
        ENDIF
        IF NOT DOES_CHAR_EXIST ai_actor
            RETURN
        ENDIF
        GET_CHAR_COORDINATES ai_actor (fAX fAY fAZ)
        GET_DISTANCE_BETWEEN_COORDS_3D fAX fAY fAZ fX fY fZ (fD)
        IF fD < 4.0
            RETURN
        ENDIF
        IF fD < 9.0
            GOSUB ai_walk_to
        ELSE
            GOSUB ai_leap_to
        ENDIF
        WAIT 100
    ENDWHILE
RETURN

//short distance: run (cheap, no collision tricks)
ai_walk_to:
    TASK_GO_TO_COORD_ANY_MEANS ai_actor fX fY fZ PEDMOVE_RUN 0
    iTick = 0
    WHILE iTick < 16
        iTick ++
        WAIT 250
        IF NOT DOES_CHAR_EXIST ai_actor
            RETURN
        ENDIF
        GET_CHAR_COORDINATES ai_actor (fAX fAY fAZ)
        GET_DISTANCE_BETWEEN_COORDS_3D fAX fAY fAZ fX fY fZ (fD)
        IF fD < 2.5
            RETURN
        ENDIF
    ENDWHILE
RETURN

//one "web swing": moves through the air (collisions off) in an arc
ai_leap_to:
    GET_CHAR_COORDINATES ai_actor (fAX fAY fAZ)
    GET_DISTANCE_BETWEEN_COORDS_3D fAX fAY fAZ fX fY fZ (fD)
    IF fD < 2.0
        RETURN
    ENDIF
    CLEAR_CHAR_TASKS ai_actor
    SET_CHAR_COLLISION ai_actor FALSE
    TASK_PLAY_ANIM_NON_INTERRUPTABLE ai_actor ("jump_glide_A" "spider") 25.0 (0 1 1 0) -1
    iTick = 0
    WHILE iTick < ai_max_leap_ticks
        iTick ++
        IF NOT DOES_CHAR_EXIST ai_actor
            RETURN
        ENDIF
        GET_CHAR_COORDINATES ai_actor (fAX fAY fAZ)
        GET_DISTANCE_BETWEEN_COORDS_3D fAX fAY fAZ fX fY fZ (fD)
        IF fD < 2.0
            BREAK
        ENDIF
        //faces the direction of the swing
        fP = fX
        fP -= fAX
        fA = fY
        fA -= fAY
        GET_HEADING_FROM_VECTOR_2D fP fA (fP)
        SET_CHAR_HEADING ai_actor fP
        //horizontal interpolation (keeps 72% of the distance per tick)
        fP = fX
        fP -= fAX
        fP *= 0.72
        fAX = fX
        fAX -= fP
        fP = fY
        fP -= fAY
        fP *= 0.72
        fAY = fY
        fAY -= fP
        //vertical: goes up on the first half, comes down on the second
        IF iTick < 7
            fP = fG
            fP += ai_arc_height
        ELSE
            fP = fZ
        ENDIF
        fA = fP
        fA -= fAZ
        fA *= 0.60
        fAZ += fA
        SET_CHAR_COORDINATES ai_actor fAX fAY fAZ
        WAIT 110
        IF iTick = 5
            GOSUB ai_shouldabort
            IF iTemp = 1
                BREAK
            ENDIF
        ENDIF
    ENDWHILE
    SET_CHAR_COORDINATES ai_actor fX fY fZ
    SET_CHAR_COLLISION ai_actor TRUE
    CLEAR_CHAR_TASKS ai_actor
    WAIT 120
RETURN

//-+-----------------------------------------------------------------
//-+ FIGHT (street crime)
//-+-----------------------------------------------------------------
//keeps a safe z when the ground query fails (fG < 0)
ai_fix_ground:
    IF fG < -1.0
        fG = fAZ
    ENDIF
    fAZ = fG + 0.5
RETURN

ai_spawn_thugs:
    iTarget = 0
    iTarget2 = 0
    iTarget3 = 0
    //thug 1 - goes after the AI
    GET_OFFSET_FROM_CHAR_IN_WORLD_COORDS ai_actor 2.5 3.0 0.0 (fAX fAY fAZ)
    GET_GROUND_Z_FOR_3D_COORD fAX fAY fAZ (fG)
    GOSUB ai_fix_ground
    CREATE_CHAR PEDTYPE_MISSION1 BALLAS1 fAX fAY fAZ (iTarget)
    IF DOES_CHAR_EXIST iTarget
        SET_CHAR_HEALTH iTarget ai_thug_health
        SET_CHAR_ACCURACY iTarget 55
        GIVE_WEAPON_TO_CHAR iTarget WEAPONTYPE_KNIFE 1
        SET_CURRENT_CHAR_WEAPON iTarget WEAPONTYPE_KNIFE
        SET_CHAR_DROPS_WEAPONS_WHEN_DEAD iTarget TRUE
        TASK_KILL_CHAR_ON_FOOT iTarget ai_actor
    ENDIF
    //thug 2 - goes after the AI
    GET_OFFSET_FROM_CHAR_IN_WORLD_COORDS ai_actor -3.0 -2.0 0.0 (fAX fAY fAZ)
    GET_GROUND_Z_FOR_3D_COORD fAX fAY fAZ (fG)
    GOSUB ai_fix_ground
    CREATE_CHAR PEDTYPE_MISSION1 BALLAS2 fAX fAY fAZ (iTarget2)
    IF DOES_CHAR_EXIST iTarget2
        SET_CHAR_HEALTH iTarget2 ai_thug_health
        SET_CHAR_ACCURACY iTarget2 55
        GIVE_WEAPON_TO_CHAR iTarget2 WEAPONTYPE_BASEBALLBAT 1
        SET_CURRENT_CHAR_WEAPON iTarget2 WEAPONTYPE_BASEBALLBAT
        SET_CHAR_DROPS_WEAPONS_WHEN_DEAD iTarget2 TRUE
        TASK_KILL_CHAR_ON_FOOT iTarget2 ai_actor
    ENDIF
    //thug 3 - goes after the player (only if he is near the crime scene)
    IF LOCATE_CHAR_DISTANCE_TO_CHAR player_actor ai_actor 60.0
        GET_OFFSET_FROM_CHAR_IN_WORLD_COORDS ai_actor -4.0 4.0 0.0 (fAX fAY fAZ)
        GET_GROUND_Z_FOR_3D_COORD fAX fAY fAZ (fG)
        GOSUB ai_fix_ground
        CREATE_CHAR PEDTYPE_MISSION1 VMAFF1 fAX fAY fAZ (iTarget3)
        IF DOES_CHAR_EXIST iTarget3
            SET_CHAR_HEALTH iTarget3 ai_thug_health
            GIVE_WEAPON_TO_CHAR iTarget3 WEAPONTYPE_KNIFE 1
            SET_CURRENT_CHAR_WEAPON iTarget3 WEAPONTYPE_KNIFE
            SET_CHAR_DROPS_WEAPONS_WHEN_DEAD iTarget3 TRUE
            TASK_KILL_CHAR_ON_FOOT iTarget3 player_actor
        ENDIF
    ENDIF
RETURN

//picks the nearest alive thug and attacks it (returns alive count in iCount)
ai_fight_tick:
    iCount = 0
    iTemp = 0   //chosen target
    IF DOES_CHAR_EXIST iTarget
        IF NOT IS_CHAR_DEAD iTarget
            iCount ++
            iTemp = iTarget
        ENDIF
    ENDIF
    IF DOES_CHAR_EXIST iTarget2
        IF NOT IS_CHAR_DEAD iTarget2
            iCount ++
            IF iTemp = 0
                iTemp = iTarget2
            ENDIF
        ENDIF
    ENDIF
    IF DOES_CHAR_EXIST iTarget3
        IF NOT IS_CHAR_DEAD iTarget3
            iCount ++
            IF iTemp = 0
                iTemp = iTarget3
            ENDIF
        ENDIF
    ENDIF
    IF iCount > 0
        IF NOT DOES_CHAR_EXIST ai_actor
            RETURN
        ENDIF
        IF IS_CHAR_DEAD ai_actor
            RETURN
        ENDIF
        //the AI shoots webs when the thug is close, otherwise it runs after him
        IF LOCATE_CHAR_DISTANCE_TO_CHAR ai_actor iTemp 9.0
            GOSUB ai_web_blast
        ELSE
            TASK_KILL_CHAR_ON_FOOT ai_actor iTemp
        ENDIF
    ENDIF
RETURN

//web shot: animation + damage (iTemp = victim)
ai_web_blast:
    IF NOT DOES_CHAR_EXIST iTemp
        RETURN
    ENDIF
    IF IS_CHAR_DEAD iTemp
        RETURN
    ENDIF
    GET_CHAR_COORDINATES iTemp (fAX fAY fAZ)
    TASK_TURN_CHAR_TO_FACE_COORD ai_actor fAX fAY fAZ
    TASK_PLAY_ANIM_NON_INTERRUPTABLE ai_actor ("sp_we_a" "spider") 55.0 (0 1 1 0) 600
    GET_CHAR_HEALTH iTemp (iRand)
    IF iRand > ai_web_damage
        iRand -= ai_web_damage
    ELSE
        iRand = 0
    ENDIF
    SET_CHAR_HEALTH iTemp iRand
RETURN

ai_clear_thugs:
    IF DOES_CHAR_EXIST iTarget
        MARK_CHAR_AS_NO_LONGER_NEEDED iTarget
    ENDIF
    IF DOES_CHAR_EXIST iTarget2
        MARK_CHAR_AS_NO_LONGER_NEEDED iTarget2
    ENDIF
    IF DOES_CHAR_EXIST iTarget3
        MARK_CHAR_AS_NO_LONGER_NEEDED iTarget3
    ENDIF
    iTarget = 0
    iTarget2 = 0
    iTarget3 = 0
RETURN

//-+-----------------------------------------------------------------
//-+ FIGHT (car chase)
//-+-----------------------------------------------------------------
ai_drive_away:
    GENERATE_RANDOM_FLOAT_IN_RANGE -30.0 30.0 (fA)
    GET_OFFSET_FROM_CHAR_IN_WORLD_COORDS player_actor fA 250.0 0.0 (fX fY fZ)
    GET_GROUND_Z_FOR_3D_COORD fX fY fZ (fG)
    IF fG > -1.0
        fZ = fG + 0.5
    ENDIF
    TASK_CAR_DRIVE_TO_COORD iDriver iCar fX fY fZ 30.0 MODE_NORMAL 1 0
RETURN

//webs the car (iTick = 1 when the car is done)
ai_web_car:
    iTick = 0
    TASK_TURN_CHAR_TO_FACE_COORD ai_actor fX fY fZ
    TASK_PLAY_ANIM_NON_INTERRUPTABLE ai_actor ("sp_we_b" "spider") 55.0 (0 1 1 0) 800
    GET_CAR_HEALTH iCar (iRand)
    IF iRand > 250
        iRand -= 250
        SET_CAR_HEALTH iCar iRand
        //keeps the driver running away
        GOSUB ai_drive_away
    ELSE
        SET_CAR_HEALTH iCar 250
        SET_CAR_FORWARD_SPEED iCar 0.0
        iTick = 1
    ENDIF
RETURN

ai_clear_chase:
    IF iCar > 0
        IF DOES_VEHICLE_EXIST iCar
            MARK_CAR_AS_NO_LONGER_NEEDED iCar
        ENDIF
        iCar = 0
    ENDIF
    IF iDriver > 0
        IF DOES_CHAR_EXIST iDriver
            IF NOT IS_CHAR_DEAD iDriver
                TASK_WANDER_STANDARD iDriver
            ENDIF
            MARK_CHAR_AS_NO_LONGER_NEEDED iDriver
        ENDIF
        iDriver = 0
    ENDIF
RETURN

//-+-----------------------------------------------------------------
//-+ END OF ENCOUNTER
//-+-----------------------------------------------------------------
cleanup:
    IF DOES_BLIP_EXIST ai_blip
        REMOVE_BLIP ai_blip
    ENDIF
    ai_blip = 0
    GOSUB ai_clear_thugs
    GOSUB ai_clear_chase
    IF DOES_CHAR_EXIST ai_actor
        SET_CHAR_COLLISION ai_actor TRUE
        IF IS_CHAR_ON_SCREEN ai_actor
            MARK_CHAR_AS_NO_LONGER_NEEDED ai_actor
        ELSE
            DELETE_CHAR ai_actor
        ENDIF
    ENDIF
    ai_actor = 0
    GOSUB unload_assets
    GOSUB update_stat_encounter
RETURN

//-+-----------------------------------------------------------------
//-+ MESSAGES (never used in AI ONLY mode)
//-+-----------------------------------------------------------------
ai_say:
    IF mode = AI_MODE_ONLY
        RETURN
    ENDIF
    SWITCH iRand
        CASE 1  //arrive
            IF iLang = 1
                PRINT_FORMATTED_NOW "~b~~h~Spider-Man~w~: I am swinging by. Stay safe!" 3500
            ELSE
                PRINT_FORMATTED_NOW "~b~~h~Spider-Man~w~: Solo pasaba por aqui. Cuidese!" 3500
            ENDIF
            BREAK
        CASE 2  //crime found
            IF iLang = 1
                PRINT_FORMATTED_NOW "~b~~h~Spider-Man~w~: Hey! Leave them alone!" 3000
            ELSE
                PRINT_FORMATTED_NOW "~b~~h~Spider-Man~w~: Oye! Dejalos en paz!" 3000
            ENDIF
            BREAK
        CASE 3  //crime done
            IF iLang = 1
                PRINT_FORMATTED_NOW "~b~~h~Spider-Man~w~: All clear here." 3000
            ELSE
                PRINT_FORMATTED_NOW "~b~~h~Spider-Man~w~: Todo despejado por aqui." 3000
            ENDIF
            BREAK
        CASE 4  //chase found
            IF iLang = 1
                PRINT_FORMATTED_NOW "~b~~h~Spider-Man~w~: You can't outrun a spider!" 3000
            ELSE
                PRINT_FORMATTED_NOW "~b~~h~Spider-Man~w~: No puedes escapar de una arana!" 3000
            ENDIF
            BREAK
        CASE 5  //chase done
            IF iLang = 1
                PRINT_FORMATTED_NOW "~b~~h~Spider-Man~w~: Got him. Nice driving, huh?" 3000
            ELSE
                PRINT_FORMATTED_NOW "~b~~h~Spider-Man~w~: Lo atrape. Bonita conduccion, eh?" 3000
            ENDIF
            BREAK
        DEFAULT //leave
            IF iLang = 1
                PRINT_FORMATTED_NOW "~b~~h~Spider-Man~w~: I have to go. See you around!" 3000
            ELSE
                PRINT_FORMATTED_NOW "~b~~h~Spider-Man~w~: Tengo que irme. Nos vemos!" 3000
            ENDIF
            BREAK
    ENDSWITCH
RETURN

//-+-----------------------------------------------------------------
//-+ STATISTICS (config.ini -> [ai_stats])
//-+-----------------------------------------------------------------
update_stat_encounter:
    IF DOES_FILE_EXIST "CLEO\SpiderJ16D\config.ini"
        iTemp = 0
        READ_INT_FROM_INI_FILE "CLEO\SpiderJ16D\config.ini" "ai_stats" "encounters" (iTemp)
        iTemp ++
        WRITE_INT_TO_INI_FILE iTemp "CLEO\SpiderJ16D\config.ini" "ai_stats" "encounters"
    ENDIF
RETURN

update_stat_crime:
    IF DOES_FILE_EXIST "CLEO\SpiderJ16D\config.ini"
        iTemp = 0
        READ_INT_FROM_INI_FILE "CLEO\SpiderJ16D\config.ini" "ai_stats" "crimes_cleared" (iTemp)
        iTemp ++
        WRITE_INT_TO_INI_FILE iTemp "CLEO\SpiderJ16D\config.ini" "ai_stats" "crimes_cleared"
    ENDIF
RETURN

update_stat_chase:
    IF DOES_FILE_EXIST "CLEO\SpiderJ16D\config.ini"
        iTemp = 0
        READ_INT_FROM_INI_FILE "CLEO\SpiderJ16D\config.ini" "ai_stats" "chases_stopped" (iTemp)
        iTemp ++
        WRITE_INT_TO_INI_FILE iTemp "CLEO\SpiderJ16D\config.ini" "ai_stats" "chases_stopped"
    ENDIF
RETURN
}
SCRIPT_END
