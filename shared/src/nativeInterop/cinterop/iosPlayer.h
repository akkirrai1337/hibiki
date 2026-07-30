#import <AVFoundation/AVFoundation.h>

static inline void hibiki_av_player_play(AVPlayer *player) {
    [player play];
}

static inline void hibiki_av_player_pause(AVPlayer *player) {
    [player pause];
}

static inline void hibiki_av_player_set_rate(AVPlayer *player, float rate) {
    player.rate = rate;
}

static inline float hibiki_av_player_get_rate(AVPlayer *player) {
    return player.rate;
}

static inline double hibiki_av_player_get_position_seconds(AVPlayer *player) {
    return CMTimeGetSeconds(player.currentTime);
}

static inline double hibiki_av_player_get_duration_seconds(AVPlayer *player) {
    return CMTimeGetSeconds(player.currentItem.duration);
}

static inline void hibiki_av_player_seek_seconds(AVPlayer *player, double seconds) {
    [player seekToTime:CMTimeMakeWithSeconds(seconds, 600)];
}
