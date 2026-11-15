from ai.tracking import ByteTrackerLite, Detection, tracks_to_units


def mk(cls="hog-rider", conf=0.9, x=0.2, y=0.2, size=0.06, team="enemy"):
    return Detection(bbox=(x - size / 2, y - size / 2, x + size / 2, y + size / 2),
                     cls_name=cls, conf=conf, team=team)


def test_same_object_keeps_id():
    tr = ByteTrackerLite()
    tr.update([mk(x=0.20, y=0.20)])
    tr.update([mk(x=0.21, y=0.21)])          # confirm_hits=2 -> born here
    active3, _, _ = tr.update([mk(x=0.22, y=0.22)])
    assert len(active3) == 1
    tr.update([mk(x=0.23, y=0.23)])
    assert active3[0].id in [t.id for t in tr.tracks]
    assert tr.tracks[0].velocity[0] > 0 and tr.tracks[0].velocity[1] > 0


def test_two_units_get_distinct_ids():
    tr = ByteTrackerLite()
    tr.update([mk(x=0.2, y=0.2), mk(x=0.8, y=0.2)])
    assert len(tr.tracks) == 2
    assert len({t.id for t in tr.tracks}) == 2


def test_track_survives_short_occlusion_and_dies_after_max_age():
    tr = ByteTrackerLite(max_age=3)
    tr.update([mk(x=0.2, y=0.2)])
    tr.update([mk(x=0.21, y=0.21)])
    tr.update([])   # occluded, time_since_update=1
    tr.update([])   # =2
    tr.update([])   # =3 (<= max_age, still alive)
    assert len(tr.tracks) == 1
    tr.update([])   # =4 > max_age -> dead
    assert len(tr.tracks) == 0


def test_low_conf_detection_keeps_track_alive():
    tr = ByteTrackerLite(max_age=2)
    tr.update([mk(x=0.2, y=0.2)])
    tr.update([mk(conf=0.2, x=0.22, y=0.22)])     # low conf (stage 2 rescue)
    assert tr.tracks[0].time_since_update == 0
    c = tr.tracks[0].center
    assert abs(c[0] - 0.22) < 0.02 and abs(c[1] - 0.22) < 0.02


def test_team_mismatch_never_matches():
    tr = ByteTrackerLite()
    tr.update([mk(x=0.2, y=0.2, team="enemy")])
    tr.update([mk(x=0.2, y=0.2, team="own")])      # same box, other team
    assert len(tr.tracks) == 2


def test_tracks_to_units_team_inference_and_feet_position():
    tr = ByteTrackerLite()
    tr.update([mk(x=0.2, y=0.3, team="enemy")])
    tr.update([mk(cls="knight", x=0.6, y=0.7, team="own")])
    units = tracks_to_units(tr.tracks, fps=10)
    by_id = {u.track_id: u for u in units}
    assert {u.team for u in units} == {"enemy", "own"}
    for t in tr.tracks:
        u = by_id[t.id]
        assert abs(u.pos.y - t.bbox[3]) < 1e-9     # feet = bbox bottom


def test_speed_estimation_tiles_per_second():
    tr = ByteTrackerLite()
    # 0.02 normalized y per frame at 10 fps -> 0.02*32*10 = 6.4 tiles/s target
    tr.update([mk(x=0.2, y=0.30, team="enemy")])
    tr.update([mk(x=0.2, y=0.32, team="enemy")])
    tr.update([mk(x=0.2, y=0.34, team="enemy")])
    tr.update([mk(x=0.2, y=0.36, team="enemy")])
    units = tracks_to_units(tr.tracks, fps=10)
    assert units[0].speed_tiles_per_sec() > 3


def test_confirmed_only_reported_as_active():
    tr = ByteTrackerLite(confirm_hits=2)
    active, born, _ = tr.update([mk(x=0.2, y=0.2)])
    assert active == [] and born == []
    active, born, _ = tr.update([mk(x=0.21, y=0.21)])
    assert active and born
